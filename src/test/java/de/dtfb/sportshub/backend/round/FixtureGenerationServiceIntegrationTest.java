package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.support.TestIds;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link FixtureGenerationService}, end-to-end through {@code POST /v1/groups/{id}/fixtures/generate}:
 * the circle-method round-robin pairing (single + double, even + odd team counts), the WINDOW-mode
 * default date/window computation, fixed slots (time + venue per round), the regeneration guard and
 * deleting a plan. See docs/12-matchday-scheduling.md.
 */
class FixtureGenerationServiceIntegrationTest extends AuthorizedControllerTest {

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    MatchDayRepository matchDayRepository;

    private String leagueId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = id(mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"2025\",\"federationId\":\"%s\"}", federationId)))
            .andExpect(status().isCreated()).andReturn());
        String categoryId = createCategory();
        leagueId = id(mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"Liga\",\"seasonId\":\"%s\",\"categoryId\":\"%s\"}", seasonId, categoryId)))
            .andExpect(status().isCreated()).andReturn());
    }

    @Test
    void evenTeamCount_singleRoundRobin_generatesEveryPairingExactlyOnce() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        List<String> teamIds = List.of(placeTeam(groupId, "A"), placeTeam(groupId, "B"),
            placeTeam(groupId, "C"), placeTeam(groupId, "D"));

        String json = generate(groupId, false);
        List<?> rounds = JsonPath.read(json, "$");
        Assertions.assertThat(rounds).hasSize(3); // n-1 rounds for 4 teams

        List<String> roundIds = JsonPath.read(json, "$[*].id");
        List<Object[]> pairs = matchDayPairsForRounds(roundIds);
        Assertions.assertThat(pairs).hasSize(6); // C(4,2)
        assertEveryPairDistinctAndFromTeamSet(pairs, teamIds);
    }

    @Test
    void oddTeamCount_byesLeaveOneTeamOutEachRound() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        List<String> teamIds = List.of(placeTeam(groupId, "A"), placeTeam(groupId, "B"), placeTeam(groupId, "C"));

        String json = generate(groupId, false);
        List<?> rounds = JsonPath.read(json, "$");
        Assertions.assertThat(rounds).hasSize(3); // n-1 rounds, n padded to 4 with a bye

        List<String> roundIds = JsonPath.read(json, "$[*].id");
        List<Object[]> pairs = matchDayPairsForRounds(roundIds);
        Assertions.assertThat(pairs).hasSize(3); // C(3,2) real games; the 4th slot each round is a bye
        assertEveryPairDistinctAndFromTeamSet(pairs, teamIds);
    }

    @Test
    void doubleRoundRobin_mirrorsEveryPairingWithHomeAwaySwapped() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");
        placeTeam(groupId, "C");
        placeTeam(groupId, "D");

        String json = generate(groupId, true);
        List<?> rounds = JsonPath.read(json, "$");
        Assertions.assertThat(rounds).hasSize(6); // 2 * (n-1)

        List<String> roundIds = JsonPath.read(json, "$[*].id");
        List<Object[]> pairs = matchDayPairsForRounds(roundIds);
        Assertions.assertThat(pairs).hasSize(12); // each of the 6 unordered pairs, twice

        Set<String> unorderedPairs = new HashSet<>();
        for (Object[] pair : pairs) {
            unorderedPairs.add(unorderedKey((String) pair[0], (String) pair[1]));
        }
        Assertions.assertThat(unorderedPairs).hasSize(6);
    }

    @Test
    void windowMode_roundGetsAWindowAndFixturesDefaultInsideIt() throws Exception {
        String groupId = createGroup("WINDOW", 7);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");

        String json = generate(groupId, false);
        Instant windowStart = Instant.parse(JsonPath.read(json, "$[0].windowStart"));
        Instant windowEnd = Instant.parse(JsonPath.read(json, "$[0].windowEnd"));
        Assertions.assertThat(windowEnd).isAfter(windowStart);

        List<String> roundIds = JsonPath.read(json, "$[*].id");
        List<Object[]> pairs = matchDayPairsForRounds(List.of(roundIds.get(0)));
        Assertions.assertThat(pairs).hasSize(1);

        String matchDaysJson = mockMvc.perform(get("/v1/matchdays"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Object> all = JsonPath.read(matchDaysJson, "$");
        Instant startDate = null;
        for (Object raw : all) {
            @SuppressWarnings("unchecked")
            var matchDay = (java.util.Map<String, Object>) raw;
            if (roundIds.get(0).equals(matchDay.get("roundId"))) {
                startDate = Instant.parse((String) matchDay.get("startDate"));
            }
        }
        Assertions.assertThat(startDate).isEqualTo(windowStart);
        Assertions.assertThat(startDate).isBetween(windowStart, windowEnd);
    }

    @Test
    void regenerating_isRejectedWithConflict() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");

        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2025-03-01T00:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2025-03-01T00:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isConflict());
    }

    @Test
    void missingSchedulingModeOnRuleSet_isRejectedWithConflict() throws Exception {
        // A tier whose rule set (and league/federation default) never configured a scheduling mode.
        String tierId = id(mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"1. Liga\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isCreated()).andReturn());
        String groupId = id(mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Gruppe A\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", tierId)))
            .andExpect(status().isCreated()).andReturn());
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");

        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2025-03-01T00:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isConflict());
    }

    @Test
    void slots_giveEveryRoundItsKickOffAndVenue_confirmedRightAway() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        for (String name : List.of("A", "B", "C", "D")) {
            placeTeam(groupId, name);
        }
        String venueId = createLocation("Halle Nord");
        List<String> kickOffs = List.of("2027-03-20T09:00:00Z", "2027-03-20T12:00:00Z", "2027-03-21T08:30:00Z");

        String json = mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotsBody(kickOffs, venueId)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> roundIds = JsonPath.read(json, "$[*].id");
        Assertions.assertThat(roundIds).hasSize(3);
        List<MatchDay> matchDays = matchDayRepository.findByRoundGroupId(groupId);
        Assertions.assertThat(matchDays).hasSize(6);
        for (MatchDay matchDay : matchDays) {
            int roundIndex = matchDay.getRound().getIndex();
            Assertions.assertThat(matchDay.getStartDate()).isEqualTo(Instant.parse(kickOffs.get(roundIndex - 1)));
            Assertions.assertThat(matchDay.getLocation().getId()).isEqualTo(venueId);
            Assertions.assertThat(matchDay.getSchedulingState())
                .isEqualTo(SchedulingState.CONFIRMED);
        }
    }

    @Test
    void slots_mustMatchTheRoundCount() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");
        placeTeam(groupId, "C");
        placeTeam(groupId, "D");

        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotsBody(List.of("2027-03-20T09:00:00Z", "2027-03-20T12:00:00Z"), null)))
            .andExpect(status().isBadRequest());
        Assertions.assertThat(matchDayRepository.findByRoundGroupId(groupId)).isEmpty();
    }

    @Test
    void slots_mustBeAscending() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");
        placeTeam(groupId, "C");

        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotsBody(
                    List.of("2027-03-20T12:00:00Z", "2027-03-20T09:00:00Z", "2027-03-21T08:30:00Z"), null)))
            .andExpect(status().isBadRequest());
    }

    @Test
    void slots_inWindowMode_areRejectedWithConflict() throws Exception {
        String groupId = createGroup("WINDOW", 7);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");

        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotsBody(List.of("2027-03-20T09:00:00Z"), null)))
            .andExpect(status().isConflict());
    }

    @Test
    void deletingAPlan_allowsGeneratingItAgain() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");
        generate(groupId, false);

        mockMvc.perform(delete("/v1/groups/" + groupId + "/fixtures"))
            .andExpect(status().isNoContent());
        Assertions.assertThat(matchDayRepository.findByRoundGroupId(groupId)).isEmpty();

        String json = generate(groupId, true);
        Assertions.assertThat((List<?>) JsonPath.read(json, "$")).hasSize(2);
    }

    @Test
    void deletingAPlan_withAnEnteredResult_isRejectedWithConflict() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        placeTeam(groupId, "A");
        placeTeam(groupId, "B");
        generate(groupId, false);
        MatchDay matchDay = matchDayRepository.findByRoundGroupId(groupId).getFirst();
        matchDay.setResultState(ResultState.SUBMITTED);
        matchDayRepository.save(matchDay);

        mockMvc.perform(delete("/v1/groups/" + groupId + "/fixtures"))
            .andExpect(status().isConflict());
        Assertions.assertThat(matchDayRepository.findByRoundGroupId(groupId)).hasSize(1);
    }

    @Test
    void schedule_listsRoundsInOrderWithNamedFixtures() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        for (String name : List.of("A", "B", "C", "D")) {
            placeTeam(groupId, name);
        }
        String venueId = createLocation("Halle Süd");
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(slotsBody(
                    List.of("2027-03-20T09:00:00Z", "2027-03-20T12:00:00Z", "2027-03-21T08:30:00Z"), venueId)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get("/v1/groups/" + groupId + "/schedule"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        Assertions.assertThat((String) JsonPath.read(json, "$.groupName")).isEqualTo("Gruppe A");
        Assertions.assertThat((String) JsonPath.read(json, "$.leagueId")).isEqualTo(leagueId);
        List<Integer> indexes = JsonPath.read(json, "$.rounds[*].index");
        Assertions.assertThat(indexes).containsExactly(1, 2, 3);
        List<?> firstRound = JsonPath.read(json, "$.rounds[0].fixtures");
        Assertions.assertThat(firstRound).hasSize(2);
        Assertions.assertThat((String) JsonPath.read(json, "$.rounds[0].fixtures[0].locationName"))
            .isEqualTo("Halle Süd");
        Assertions.assertThat((String) JsonPath.read(json, "$.rounds[0].fixtures[0].teamHomeName")).isNotBlank();
        Assertions.assertThat((String) JsonPath.read(json, "$.rounds[2].fixtures[0].startDate"))
            .isEqualTo("2027-03-21T08:30:00Z");
    }

    @Test
    void schedule_ofAGroupWithoutPlan_isEmpty() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        String json = mockMvc.perform(get("/v1/groups/" + groupId + "/schedule"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Assertions.assertThat((List<?>) JsonPath.read(json, "$.rounds")).isEmpty();
    }

    @Test
    void schedule_ofAnUnknownGroup_isNotFound() throws Exception {
        mockMvc.perform(get("/v1/groups/does-not-exist/schedule"))
            .andExpect(status().isNotFound());
    }

    @Test
    void roundSpacing_setsTheGapBetweenProvisionalRoundDates() throws Exception {
        String groupId = createGroup("DAY_BATCH", null);
        for (String name : List.of("A", "B", "C", "D")) {
            placeTeam(groupId, name);
        }
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2027-01-09T00:00:00Z\",\"doubleRoundRobin\":false,\"roundSpacingDays\":14}"))
            .andExpect(status().isOk());

        for (MatchDay matchDay : matchDayRepository.findByRoundGroupId(groupId)) {
            long offset = (matchDay.getRound().getIndex() - 1) * 14L;
            Assertions.assertThat(matchDay.getStartDate())
                .isEqualTo(Instant.parse("2027-01-09T00:00:00Z").plus(offset, java.time.temporal.ChronoUnit.DAYS));
            Assertions.assertThat(matchDay.getSchedulingState()).isEqualTo(SchedulingState.DEFAULT);
        }
    }

    @Test
    void roundSpacing_isRejectedInWindowMode_withSlots_andWhenNotPositive() throws Exception {
        String windowGroup = createGroup("WINDOW", 7);
        placeTeam(windowGroup, "A");
        placeTeam(windowGroup, "B");
        mockMvc.perform(post("/v1/groups/" + windowGroup + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2027-01-09T00:00:00Z\",\"roundSpacingDays\":14}"))
            .andExpect(status().isConflict());

        String batchGroup = createGroup("DAY_BATCH", null);
        placeTeam(batchGroup, "C");
        placeTeam(batchGroup, "D");
        mockMvc.perform(post("/v1/groups/" + batchGroup + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roundSpacingDays\":14,\"slots\":[{\"startDate\":\"2027-03-20T09:00:00Z\"}]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/groups/" + batchGroup + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2027-01-09T00:00:00Z\",\"roundSpacingDays\":0}"))
            .andExpect(status().isBadRequest());
        Assertions.assertThat(matchDayRepository.findByRoundGroupId(batchGroup)).isEmpty();
    }

    @Test
    void groupRules_returnTheRulesTheGeneratorReads() throws Exception {
        String groupId = createGroup("WINDOW", 10);
        String json = mockMvc.perform(get("/v1/groups/" + groupId + "/rules"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        Assertions.assertThat((String) JsonPath.read(json, "$.schedulingMode")).isEqualTo("WINDOW");
        Assertions.assertThat((Integer) JsonPath.read(json, "$.schedulingWindowDays")).isEqualTo(10);
    }

    // --- helpers ---

    private String slotsBody(List<String> kickOffs, String locationId) {
        StringBuilder slots = new StringBuilder();
        for (String kickOff : kickOffs) {
            if (!slots.isEmpty()) {
                slots.append(',');
            }
            slots.append(locationId == null
                ? String.format("{\"startDate\":\"%s\"}", kickOff)
                : String.format("{\"startDate\":\"%s\",\"locationId\":\"%s\"}", kickOff, locationId));
        }
        return "{\"doubleRoundRobin\":false,\"slots\":[" + slots + "]}";
    }

    private String createLocation(String name) {
        Location location = new Location();
        location.setName(name);
        return locationRepository.save(location).getId();
    }

    private void assertEveryPairDistinctAndFromTeamSet(List<Object[]> pairs, List<String> teamIds) {
        Set<String> seen = new HashSet<>();
        for (Object[] pair : pairs) {
            String home = (String) pair[0];
            String away = (String) pair[1];
            Assertions.assertThat(teamIds).contains(home, away);
            Assertions.assertThat(home).isNotEqualTo(away);
            String key = unorderedKey(home, away);
            Assertions.assertThat(seen).doesNotContain(key);
            seen.add(key);
        }
    }

    /** A canonical, order-independent key for an unordered pair — {@code Set.toString()} is NOT
     * safe for this: two equal-content sets built via different insertion order can iterate (and
     * thus print) differently if their elements happen to hash-collide into the same bucket. */
    private String unorderedKey(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    private List<Object[]> matchDayPairsForRounds(List<String> roundIds) throws Exception {
        String matchDaysJson = mockMvc.perform(get("/v1/matchdays"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Object> all = JsonPath.read(matchDaysJson, "$");
        List<Object[]> pairs = new ArrayList<>();
        for (Object raw : all) {
            @SuppressWarnings("unchecked")
            var matchDay = (java.util.Map<String, Object>) raw;
            if (roundIds.contains(matchDay.get("roundId"))) {
                pairs.add(new Object[] {matchDay.get("teamHomeId"), matchDay.get("teamAwayId")});
            }
        }
        return pairs;
    }

    private String generate(String groupId, boolean doubleRoundRobin) throws Exception {
        return mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"startDate\":\"2025-03-01T00:00:00Z\",\"doubleRoundRobin\":%s}", doubleRoundRobin)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    private String createGroup(String schedulingMode, Integer schedulingWindowDays) throws Exception {
        String ruleSetBody = schedulingWindowDays == null
            // template names are unique per owner (docs/21); these DTFB-wide ones share one owner
            ? String.format("{\"name\":\"%s\",\"schedulingMode\":\"%s\"}", TestIds.unique("Testregeln"), schedulingMode)
            : String.format("{\"name\":\"%s\",\"schedulingMode\":\"%s\",\"schedulingWindowDays\":%d}",
                TestIds.unique("Testregeln"), schedulingMode, schedulingWindowDays);
        String ruleSetId = id(mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON).content(ruleSetBody))
            .andExpect(status().isCreated()).andReturn());

        String tierId = id(mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"1. Liga\",\"leagueId\":\"%s\",\"ruleSetId\":\"%s\"}", leagueId, ruleSetId)))
            .andExpect(status().isCreated()).andReturn());

        return id(mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Gruppe A\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", tierId)))
            .andExpect(status().isCreated()).andReturn());
    }

    /** Seeds a team, registers it into the group, and returns the TEAM id the participation actually
     * ends up with (fixtures pair teams, not participations) -- since the seeded team has no season
     * of its own, TeamParticipationService resolves it into the league's season, which for a
     * fresh team means a newly cloned row, not the seeded one. */
    private String placeTeam(String groupId, String name) throws Exception {
        Club club = new Club();
        club.setName(name + "-Verein");
        club.setFederationId("fed");
        clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        String teamId = teamRepository.save(team).getId();

        MvcResult result = mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", teamId, leagueId, groupId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.teamId");
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
