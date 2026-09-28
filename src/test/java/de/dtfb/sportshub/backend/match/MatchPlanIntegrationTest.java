package de.dtfb.sportshub.backend.match;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link MatchPlanService} (SPO-71): a fixture's games come from its group's effective game plan --
 * on generation, on manual creation and via the startup backfill -- and follow later changes of the
 * plan until the first result is entered; from then on the plan is fixed (409 GAME_PLAN_LOCKED).
 * See docs/12-matchday-scheduling.md §5.
 */
class MatchPlanIntegrationTest extends AuthorizedControllerTest {

    private static final String DOUBLE_DOUBLE_SINGLE =
        "[{\"position\":1,\"gameType\":\"DOUBLE\"},{\"position\":2,\"gameType\":\"DOUBLE\"},"
            + "{\"position\":3,\"gameType\":\"SINGLE\"}]";
    private static final String SINGLE_GOALIE =
        "[{\"position\":1,\"gameType\":\"SINGLE\"},{\"position\":2,\"gameType\":\"GOALIE\"}]";

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    MatchDayRepository matchDayRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    RoundRepository roundRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    MatchPlanService matchPlan;

    private String leagueId;
    private String tierId;
    private String groupId;

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

        String blueprintId = id(mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody(TestIds.unique("Spielfolge"), 2, DOUBLE_DOUBLE_SINGLE)))
            .andExpect(status().isCreated()).andReturn());
        tierId = id(mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"name\":\"1. Liga\",\"leagueId\":\"%s\",\"ruleSetId\":\"%s\"}", leagueId, blueprintId)))
            .andExpect(status().isCreated()).andReturn());
        groupId = id(mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Gruppe A\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", tierId)))
            .andExpect(status().isCreated()).andReturn());
        placeTeam("A");
        placeTeam("B");
        placeTeam("C");
        placeTeam("D");
    }

    @Test
    void generating_givesEveryFixtureTheGamesOfThePlan() throws Exception {
        generate();

        List<MatchDay> fixtures = matchDayRepository.findByRoundGroupId(groupId);
        Assertions.assertThat(fixtures).hasSize(6);
        for (MatchDay fixture : fixtures) {
            List<Match> games = games(fixture);
            Assertions.assertThat(games).extracting(Match::getPosition).containsExactly(1, 2, 3);
            Assertions.assertThat(games).extracting(Match::getType)
                .containsExactly(MatchType.DOUBLE, MatchType.DOUBLE, MatchType.SINGLE);
            Assertions.assertThat(games).allSatisfy(game -> {
                Assertions.assertThat(game.getState()).isEqualTo(MatchState.PLANNED);
                Assertions.assertThat(game.getStartTime()).isEqualTo(fixture.getStartDate());
            });
        }
    }

    @Test
    void changingThePlan_beforeAnyResult_rebuildsTheGames_andKeepsTheSchedule() throws Exception {
        generate();
        MatchDay fixture = matchDayRepository.findByRoundGroupId(groupId).getFirst();
        Instant startDate = fixture.getStartDate();

        mockMvc.perform(put("/v1/league-rule-sets/" + tierRuleSetId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Spielfolge", 2, SINGLE_GOALIE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamePlanLocked").value(false));

        for (MatchDay each : matchDayRepository.findByRoundGroupId(groupId)) {
            Assertions.assertThat(games(each)).extracting(Match::getType)
                .containsExactly(MatchType.SINGLE, MatchType.GOALIE);
        }
        MatchDay reloaded = matchDayRepository.findById(fixture.getId()).orElseThrow();
        Assertions.assertThat(reloaded.getStartDate()).isEqualTo(startDate);
    }

    @Test
    void changingThePlan_afterAResult_isRejected_andNothingChanges() throws Exception {
        generate();
        enterResult(matchDayRepository.findByRoundGroupId(groupId).getFirst());
        String ruleSetId = tierRuleSetId();

        mockMvc.perform(put("/v1/league-rule-sets/" + ruleSetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Spielfolge", 3, SINGLE_GOALIE)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("GAME_PLAN_LOCKED"));

        mockMvc.perform(get("/v1/league-rule-sets/" + ruleSetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamePlanLocked").value(true))
            .andExpect(jsonPath("$.pointsWin").value(2)) // the whole update was rolled back
            .andExpect(jsonPath("$.gamePlan.length()").value(3));
        for (MatchDay each : matchDayRepository.findByRoundGroupId(groupId)) {
            Assertions.assertThat(games(each)).hasSize(3);
        }
    }

    @Test
    void otherRules_stayEditableAfterAResult() throws Exception {
        generate();
        enterResult(matchDayRepository.findByRoundGroupId(groupId).getFirst());

        mockMvc.perform(put("/v1/league-rule-sets/" + tierRuleSetId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Spielfolge", 3, DOUBLE_DOUBLE_SINGLE)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointsWin").value(3));
    }

    @Test
    void aScoredGame_locksThePlanToo() throws Exception {
        generate();
        Match game = games(matchDayRepository.findByRoundGroupId(groupId).getFirst()).getFirst();
        game.setHomeScore(5);
        game.setAwayScore(3);
        matchRepository.save(game);

        mockMvc.perform(put("/v1/league-rule-sets/" + tierRuleSetId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Spielfolge", 2, SINGLE_GOALIE)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("GAME_PLAN_LOCKED"));
    }

    @Test
    void removingTheTierOverride_switchesToTheLeaguePlan() throws Exception {
        generate();
        setLeaguePlan(SINGLE_GOALIE);

        mockMvc.perform(put("/v1/tiers/" + tierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"1. Liga\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isOk());

        for (MatchDay each : matchDayRepository.findByRoundGroupId(groupId)) {
            Assertions.assertThat(games(each)).extracting(Match::getType)
                .containsExactly(MatchType.SINGLE, MatchType.GOALIE);
        }
    }

    @Test
    void removingTheTierOverride_afterAResult_isRejected() throws Exception {
        generate();
        setLeaguePlan(SINGLE_GOALIE);
        enterResult(matchDayRepository.findByRoundGroupId(groupId).getFirst());

        mockMvc.perform(put("/v1/tiers/" + tierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"1. Liga\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("GAME_PLAN_LOCKED"));
        Assertions.assertThat(tierRuleSetId()).isNotNull(); // the override is still there
    }

    @Test
    void changingTheLeaguePlan_leavesATierWithItsOwnPlanAlone_evenAfterAResult() throws Exception {
        generate();
        enterResult(matchDayRepository.findByRoundGroupId(groupId).getFirst());

        setLeaguePlan(SINGLE_GOALIE); // the group plays under the tier's override, not the league's

        for (MatchDay each : matchDayRepository.findByRoundGroupId(groupId)) {
            Assertions.assertThat(games(each)).hasSize(3);
        }
    }

    @Test
    void aManuallyCreatedFixture_getsItsGames_andCanBeDeleted() throws Exception {
        generate();
        MatchDay template = matchDayRepository.findByRoundGroupId(groupId).getFirst();
        String fixtureId = id(mockMvc.perform(post("/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"roundId\":\"%s\",\"teamHomeId\":\"%s\",\"teamAwayId\":\"%s\",\"startDate\":\"2025-05-01T10:00:00Z\"}",
                    template.getRound().getId(), template.getTeamHome().getId(), template.getTeamAway().getId())))
            .andExpect(status().isCreated()).andReturn());
        MatchDay created = matchDayRepository.findById(fixtureId).orElseThrow();
        Assertions.assertThat(games(created)).hasSize(3);

        mockMvc.perform(delete("/v1/matchdays/" + fixtureId))
            .andExpect(status().is2xxSuccessful());
        Assertions.assertThat(matchDayRepository.findById(fixtureId)).isEmpty();
    }

    @Test
    void deletingThePlan_removesTheGamesToo() throws Exception {
        generate();
        List<MatchDay> fixtures = matchDayRepository.findByRoundGroupId(groupId);

        mockMvc.perform(delete("/v1/groups/" + groupId + "/fixtures"))
            .andExpect(status().isNoContent());
        for (MatchDay fixture : fixtures) {
            Assertions.assertThat(matchRepository.findByMatchDay(fixture)).isEmpty();
        }
    }

    @Test
    void backfill_givesOldOpenFixturesTheirGames_andSkipsFixturesWithAResult() {
        Round round = new Round();
        round.setGroup(groupRepository.findById(groupId).orElseThrow());
        round.setIndex(1);
        round.setName("Spieltag 1");
        round = roundRepository.save(round);
        MatchDay open = oldFixture(round, ResultState.OPEN);
        MatchDay decided = oldFixture(round, ResultState.CONFIRMED);

        Assertions.assertThat(matchPlan.backfill()).isGreaterThanOrEqualTo(1);

        Assertions.assertThat(games(open)).hasSize(3);
        Assertions.assertThat(games(decided)).isEmpty();
        Assertions.assertThat(matchPlan.backfill()).isZero();
    }

    @Test
    void backfill_doesNotCountFixturesOfAGroupWithoutGamePlan() throws Exception {
        setTierPlan("[]");
        Round round = new Round();
        round.setGroup(groupRepository.findById(groupId).orElseThrow());
        round.setIndex(1);
        round.setName("Spieltag 1");
        MatchDay planless = oldFixture(roundRepository.save(round), ResultState.OPEN);

        matchPlan.backfill();

        Assertions.assertThat(games(planless)).isEmpty();
        Assertions.assertThat(matchPlan.backfill()).isZero(); // no endless "filled" on every startup
    }

    // --- helpers ---

    private void setTierPlan(String gamePlan) throws Exception {
        mockMvc.perform(put("/v1/league-rule-sets/" + tierRuleSetId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Spielfolge", 2, gamePlan)))
            .andExpect(status().isOk());
    }

    private void setLeaguePlan(String gamePlan) throws Exception {
        String leagueJson = mockMvc.perform(get("/v1/leagues/" + leagueId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String leagueRuleSetId = JsonPath.read(leagueJson, "$.ruleSetId");
        mockMvc.perform(put("/v1/league-rule-sets/" + leagueRuleSetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(ruleSetBody("Liga-Regeln", 2, gamePlan)))
            .andExpect(status().isOk());
    }

    private MatchDay oldFixture(Round round, ResultState resultState) {
        List<Team> teams = teamRepository.findAll().subList(0, 2);
        MatchDay fixture = new MatchDay();
        fixture.setRound(round);
        fixture.setTeamHome(teams.get(0));
        fixture.setTeamAway(teams.get(1));
        fixture.setStartDate(Instant.parse("2025-04-01T10:00:00Z"));
        fixture.setResultState(resultState);
        fixture.setSchedulingState(SchedulingState.DEFAULT);
        return matchDayRepository.save(fixture);
    }

    private List<Match> games(MatchDay fixture) {
        return matchRepository.findByMatchDay(fixture).stream()
            .sorted(Comparator.comparing(Match::getPosition))
            .toList();
    }

    private void enterResult(MatchDay fixture) {
        fixture.setResultState(ResultState.SUBMITTED);
        matchDayRepository.save(fixture);
    }

    private String tierRuleSetId() throws Exception {
        String json = mockMvc.perform(get("/v1/tiers/" + tierId))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.ruleSetId");
    }

    private void generate() throws Exception {
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2025-03-01T00:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isOk());
    }

    private static String ruleSetBody(String name, int pointsWin, String gamePlan) {
        return String.format(
            "{\"name\":\"%s\",\"schedulingMode\":\"DAY_BATCH\",\"pointsWin\":%d,\"gamePlan\":%s}",
            name, pointsWin, gamePlan);
    }

    private void placeTeam(String name) throws Exception {
        Club club = new Club();
        club.setName(name + "-Verein");
        club.setFederationId("fed");
        clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        String teamId = teamRepository.save(team).getId();
        mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format(
                    "{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", teamId, leagueId, groupId)))
            .andExpect(status().isCreated());
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
