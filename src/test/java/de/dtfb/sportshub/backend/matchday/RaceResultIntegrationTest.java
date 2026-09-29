package de.dtfb.sportshub.backend.matchday;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Race to 42 end-to-end (docs/22): a league from the seeded "Race to 42 (DTFB)" blueprint, three teams
 * (so every round has a bye), segment entry with the step checks, decided on the last segment, the
 * confirmation deadline, and the table with bye wins and goals.
 */
class RaceResultIntegrationTest extends AuthorizedControllerTest {

    /** A complete race, 42 : 38, as running scores after each of the seven segments. */
    private static final int[][] FULL_RACE = {{6, 4}, {12, 9}, {18, 15}, {24, 20}, {30, 27}, {36, 33}, {42, 38}};

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    MatchDayRepository matchDayRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private String leagueId;
    private String groupId;
    private List<String> teamIds;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = id(mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"2027\",\"federationId\":\"%s\"}", federationId)))
            .andExpect(status().isCreated()).andReturn());
        String categoryId = createCategory();
        leagueId = id(mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Regionalliga\",\"seasonId\":\"%s\",\"categoryId\":\"%s\",\"blueprintId\":\"rs-race42\"}",
                    seasonId, categoryId)))
            .andExpect(status().isCreated()).andReturn());
        de.dtfb.sportshub.backend.support.LineupTestSupport.disableLineups(mockMvc, null, leagueId); // line-ups: LineupIntegrationTest
        String tierId = id(mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Vorrunde\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isCreated()).andReturn());
        groupId = id(mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Gruppe A\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", tierId)))
            .andExpect(status().isCreated()).andReturn());
        teamIds = List.of(placeTeam("A"), placeTeam("B"), placeTeam("C"));
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2027-03-20T09:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isOk());
    }

    @Test
    void theSeededBlueprint_isRaceTo42WithSevenSegments() throws Exception {
        mockMvc.perform(get("/v1/league-rule-sets/rs-race42"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fixtureMode").value("RACE"))
            .andExpect(jsonPath("$.raceTarget").value(42))
            .andExpect(jsonPath("$.raceStep").value(6))
            .andExpect(jsonPath("$.raceEndRule").value("DRAW_ALLOWED"))
            .andExpect(jsonPath("$.confirmationMinutes").value(15))
            .andExpect(jsonPath("$.gamePlan.length()").value(7));
        mockMvc.perform(get("/v1/league-rule-sets/rs-race42-ko"))
            .andExpect(jsonPath("$.raceEndRule").value("TWO_POINT_LEAD"));
    }

    @Test
    void everyRound_hasAByeFixture_thatCountsAsA42to30Win() throws Exception {
        List<MatchDay> byes = matchDayRepository.findByRoundGroupId(groupId).stream().filter(MatchDay::isBye).toList();
        assertThat(byes).hasSize(3);
        assertThat(byes).allSatisfy(bye -> {
            assertThat(bye.getTeamAway()).isNull();
            assertThat(bye.getResultState()).isEqualTo(ResultState.CONFIRMED);
            assertThat(matchRepository.findByMatchDay(bye)).isEmpty();
        });

        String table = standings();
        assertThat((List<Integer>) JsonPath.read(table, "$[*].points")).containsOnly(2);
        assertThat((List<Integer>) JsonPath.read(table, "$[*].goalsFor")).containsOnly(42);
        assertThat((List<Integer>) JsonPath.read(table, "$[*].goalsAgainst")).containsOnly(30);

        enter(ADMIN, byes.getFirst().getId(), new int[][] {{6, 4}}).andExpect(status().isConflict());
    }

    @Test
    void segmentsAreChecked_andTheLastOneDecides() throws Exception {
        MatchDay fixture = firstRealFixture();
        RequestPostProcessor home = captain("race-h", fixture.getTeamHome());

        enter(home, fixture.getId(), new int[][] {{5, 4}}).andExpect(status().isBadRequest());
        enter(home, fixture.getId(), new int[][] {{6, 4}, {12, 9}})
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fixtureMode").value("RACE"))
            .andExpect(jsonPath("$.gamesEntered").value(2))
            .andExpect(jsonPath("$.decided").value(false))
            .andExpect(jsonPath("$.confirmDeadline").isEmpty());

        enter(home, fixture.getId(), FULL_RACE)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decided").value(true))
            .andExpect(jsonPath("$.decidedAt").isNotEmpty())
            .andExpect(jsonPath("$.confirmDeadline").isNotEmpty());

        confirm(captain("race-a", fixture.getTeamAway()), fixture.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));

        String table = standings();
        List<Integer> homePoints = JsonPath.read(table, "$[?(@.teamId == '" + fixture.getTeamHome().getId() + "')].points");
        List<Integer> homeGoals = JsonPath.read(table, "$[?(@.teamId == '" + fixture.getTeamHome().getId() + "')].goalsFor");
        assertThat(homePoints).containsExactly(4);        // bye win + race win
        assertThat(homeGoals).containsExactly(42 + 42);   // bye 42 + race 42
        assertThat((Integer) JsonPath.read(table, "$[0].place")).isEqualTo(1);
    }

    @Test
    void afterTheDeadline_onlyTheTournamentManagementCanConfirm() throws Exception {
        MatchDay fixture = firstRealFixture();
        enter(captain("race-h", fixture.getTeamHome()), fixture.getId(), FULL_RACE).andExpect(status().isOk());
        MatchDay entered = matchDayRepository.findById(fixture.getId()).orElseThrow();
        entered.setDecidedAt(Instant.now().minus(20, ChronoUnit.MINUTES)); // 15 minutes to confirm
        matchDayRepository.save(entered);

        RequestPostProcessor away = captain("race-a", fixture.getTeamAway());
        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/result").with(away))
            .andExpect(jsonPath("$.overdue").value(true))
            .andExpect(jsonPath("$.canConfirm").value(false))
            .andExpect(jsonPath("$.canEdit").value(false));
        confirm(away, fixture.getId()).andExpect(status().isConflict());
        enter(away, fixture.getId(), FULL_RACE).andExpect(status().isConflict());

        confirm(ADMIN, fixture.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void aDraw41to41_isAllowedInTheVorrunde() throws Exception {
        MatchDay fixture = firstRealFixture();
        int[][] draw = {{6, 4}, {12, 9}, {18, 15}, {24, 20}, {30, 27}, {36, 33}, {41, 41}};
        enter(ADMIN, fixture.getId(), draw)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
        List<Integer> draws = JsonPath.read(standings(), "$[?(@.teamId == '" + fixture.getTeamHome().getId() + "')].draws");
        assertThat(draws).containsExactly(1);
    }

    @Test
    void pendingResults_listTheFixtureForBothCaptainsAndTheTournamentManagement_only() throws Exception {
        MatchDay fixture = firstRealFixture();
        enter(captain("race-h", fixture.getTeamHome()), fixture.getId(), FULL_RACE).andExpect(status().isOk());

        for (RequestPostProcessor who : List.of(captain("race-h", fixture.getTeamHome()),
                captain("race-a", fixture.getTeamAway()), ADMIN)) {
            String pending = mockMvc.perform(get("/v1/matchdays/pending-results").with(who))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertThat((List<String>) JsonPath.read(pending, "$[*].matchDayId")).contains(fixture.getId());
            assertThat((List<String>) JsonPath.read(pending, "$[?(@.matchDayId == '" + fixture.getId() + "')].groupName"))
                .containsExactly("Gruppe A");
        }
        String stranger = mockMvc.perform(get("/v1/matchdays/pending-results")
                .with(jwt().jwt(token -> token.claim("dtfb_id", "stranger-" + groupId))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(stranger, "$[*].matchDayId")).doesNotContain(fixture.getId());
    }

    @Test
    void listsShowTheFixtureScore_runningScoreAndProgress_andTheByeScore() throws Exception {
        MatchDay fixture = firstRealFixture();
        enter(captain("race-h", fixture.getTeamHome()), fixture.getId(), new int[][] {{6, 4}, {12, 9}, {18, 15}})
            .andExpect(status().isOk());

        String schedule = mockMvc.perform(get("/v1/groups/" + groupId + "/schedule"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String path = "$.rounds[*].fixtures[?(@.id == '" + fixture.getId() + "')]";
        assertThat((List<Integer>) JsonPath.read(schedule, path + ".scoreHome")).containsExactly(18);
        assertThat((List<Integer>) JsonPath.read(schedule, path + ".scoreAway")).containsExactly(15);
        assertThat((List<Integer>) JsonPath.read(schedule, path + ".gamesEntered")).containsExactly(3);
        assertThat((List<Integer>) JsonPath.read(schedule, path + ".gamesTotal")).containsExactly(7);
        assertThat((List<Integer>) JsonPath.read(schedule, "$.rounds[*].fixtures[?(@.bye == true)].scoreHome"))
            .containsOnly(42);

        mockMvc.perform(get("/v1/matchdays/" + fixture.getId()))
            .andExpect(jsonPath("$.scoreHome").value(18))
            .andExpect(jsonPath("$.scoreAway").value(15));
    }

    // --- helpers ---

    private static final RequestPostProcessor ADMIN = jwt().jwt(token -> token.claim("dtfb_id", "admin"));

    private MatchDay firstRealFixture() {
        return matchDayRepository.findByRoundGroupId(groupId).stream()
            .filter(matchDay -> !matchDay.isBye())
            .min(Comparator.comparing(MatchDay::getStartDate).thenComparing(MatchDay::getId))
            .orElseThrow();
    }

    private ResultActions enter(RequestPostProcessor who, String matchDayId, int[][] runningScores) throws Exception {
        List<Match> segments = matchRepository.findByMatchDay(matchDayRepository.findById(matchDayId).orElseThrow())
            .stream().sorted(Comparator.comparing(Match::getPosition)).toList();
        StringBuilder body = new StringBuilder("{\"matches\":[");
        for (int i = 0; i < runningScores.length; i++) {
            if (i > 0) body.append(',');
            String matchId = i < segments.size() ? segments.get(i).getId() : "none";
            body.append(String.format("{\"matchId\":\"%s\",\"homeScore\":%d,\"awayScore\":%d}",
                matchId, runningScores[i][0], runningScores[i][1]));
        }
        body.append("]}");
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(who)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions confirm(RequestPostProcessor who, String matchDayId) throws Exception {
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/confirm").with(who));
    }

    private String standings() throws Exception {
        return mockMvc.perform(get("/v1/groups/" + groupId + "/standings"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    /** A captain (team_admin) of the team; dtfb_ids are scoped to this test's group. */
    private RequestPostProcessor captain(String name, Team team) {
        String dtfbId = name + "-" + groupId;
        User user = userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User u = new User();
            u.setDtfbId(dtfbId);
            return userRepository.save(u);
        });
        if (roleAssignmentRepository.findByUser(user).isEmpty()) {
            RoleAssignment grant = new RoleAssignment();
            grant.setUser(user);
            grant.setRole(Role.TEAM_ADMIN);
            grant.setScopeType(ScopeType.TEAM);
            grant.setScopeId(team.getTeamIdentityId());
            grant.setCreatedAt(Instant.now());
            roleAssignmentRepository.save(grant);
        }
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private String placeTeam(String name) throws Exception {
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
                .content(String.format("{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", teamId, leagueId, groupId)))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.teamId");
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
