package de.dtfb.sportshub.backend.knockout;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.LineupTestSupport;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Knock-out stage end-to-end (docs/34): two Race to 42 groups of three, the helper's semi-finals (1A–2B,
 * 1B–2A), final, game for 3rd and game for 5th; teams fill once a group is finished or a source fixture
 * is final; level results, corrections and overrides.
 */
class KnockoutStageIntegrationTest extends AuthorizedControllerTest {

    /** Home wins 42 : 30, as running scores after each of the seven segments. */
    private static final int[][] HOME_WINS = {{6, 5}, {12, 10}, {18, 15}, {24, 20}, {30, 25}, {36, 28}, {42, 30}};

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    MatchDayRepository matchDayRepository;

    @Autowired
    MatchRepository matchRepository;

    @Autowired
    TeamParticipationRepository participationRepository;

    private String federationId;
    private String seasonId;
    private String leagueId;
    private String tierId;
    private String groupA;
    private String groupB;
    /** Team ids by name; within a group the lower number is stronger (A1 beats A2 beats A3). */
    private Map<String, String> teams;

    @BeforeEach
    void setup() throws Exception {
        federationId = createFederation();
        seasonId = id(mockMvc.perform(post("/v1/seasons").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"2027\",\"federationId\":\"%s\"}", federationId)))
            .andExpect(status().isCreated()).andReturn());
        leagueId = id(mockMvc.perform(post("/v1/leagues").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Regionalliga\",\"seasonId\":\"%s\",\"categoryId\":\"%s\",\"blueprintId\":\"rs-race42\"}",
                    seasonId, createCategory())))
            .andExpect(status().isCreated()).andReturn());
        LineupTestSupport.disableLineups(mockMvc, null, leagueId);
        tierId = id(mockMvc.perform(post("/v1/tiers").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Regionalliga\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isCreated()).andReturn());
        groupA = createGroup("Gruppe A");
        groupB = createGroup("Gruppe B");
        teams = new java.util.HashMap<>();
        for (String name : List.of("A1", "A2", "A3")) teams.put(name, placeTeam(name, groupA));
        for (String name : List.of("B1", "B2", "B3")) teams.put(name, placeTeam(name, groupB));
        for (String group : List.of(groupA, groupB)) {
            mockMvc.perform(post("/v1/groups/" + group + "/fixtures/generate").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"startDate\":\"2027-03-20T09:00:00Z\",\"doubleRoundRobin\":false}"))
                .andExpect(status().isOk());
        }
    }

    @Test
    void theHelper_buildsSemiFinalsFinalAndPlacementGames_fedByCrossedGroupPlaces() throws Exception {
        String stage = createStage("rs-race42-ko");

        Map<String, MatchDay> fixtures = fixturesByName(stage);
        assertThat(fixtures.keySet()).containsExactlyInAnyOrder("HF 1", "HF 2", "Finale", "Spiel um Platz 3", "Spiel um Platz 5");
        MatchDay semi1 = fixtures.get("HF 1");
        assertThat(semi1.getHomeSource().getGroup().getId()).isEqualTo(groupA);
        assertThat(semi1.getHomeSource().getPlace()).isEqualTo(1);
        assertThat(semi1.getAwaySource().getGroup().getId()).isEqualTo(groupB);
        assertThat(semi1.getAwaySource().getPlace()).isEqualTo(2);
        assertThat(fixtures.get("Finale").getWinnerPlace()).isEqualTo(1);
        assertThat(fixtures.get("Spiel um Platz 3").getLoserPlace()).isEqualTo(4);
        assertThat(fixtures.get("Spiel um Platz 5").getHomeSource().getPlace()).isEqualTo(3);
        // Nothing is known before the groups are played; the K.O. games have the race's seven segments.
        assertThat(fixtures.values()).allSatisfy(fixture -> {
            assertThat(fixture.getTeamHome()).isNull();
            assertThat(matchRepository.findByMatchDay(fixture)).hasSize(7);
        });
        // Teams can't be placed into a knock-out stage.
        mockMvc.perform(post("/v1/team-participations").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", newTeam("X"), leagueId, stage)))
            .andExpect(status().isConflict());
    }

    @Test
    void teamsFill_whenAGroupIsFinished_andWhenASourceFixtureIsFinal() throws Exception {
        String stage = createStage("rs-race42-ko");
        playGroup(groupA);
        // Group B still running: only A's places are known.
        MatchDay semi1 = fixturesByName(stage).get("HF 1");
        assertThat(semi1.getTeamHome().getId()).isEqualTo(teams.get("A1"));
        assertThat(semi1.getTeamAway()).isNull();
        enter(semi1.getId(), HOME_WINS).andExpect(status().isConflict()); // teams not known yet

        playGroup(groupB);
        Map<String, MatchDay> fixtures = fixturesByName(stage);
        assertThat(fixtures.get("HF 1").getTeamAway().getId()).isEqualTo(teams.get("B2"));
        assertThat(fixtures.get("HF 2").getTeamHome().getId()).isEqualTo(teams.get("B1"));
        assertThat(fixtures.get("HF 2").getTeamAway().getId()).isEqualTo(teams.get("A2"));
        assertThat(fixtures.get("Spiel um Platz 5").getTeamHome().getId()).isEqualTo(teams.get("A3"));
        assertThat(fixtures.get("Finale").getTeamHome()).isNull();

        enter(fixtures.get("HF 1").getId(), HOME_WINS).andExpect(status().isOk()); // A1 wins, final at once (admin)
        enter(fixtures.get("HF 2").getId(), HOME_WINS).andExpect(status().isOk()); // B1 wins
        fixtures = fixturesByName(stage);
        assertThat(fixtures.get("Finale").getTeamHome().getId()).isEqualTo(teams.get("A1"));
        assertThat(fixtures.get("Finale").getTeamAway().getId()).isEqualTo(teams.get("B1"));
        assertThat(fixtures.get("Spiel um Platz 3").getTeamHome().getId()).isEqualTo(teams.get("B2"));
        assertThat(fixtures.get("Spiel um Platz 3").getTeamAway().getId()).isEqualTo(teams.get("A2"));
    }

    @Test
    void aCorrection_isRefused_onceALaterFixtureItFedHasAResult() throws Exception {
        String stage = createStage("rs-race42-ko");
        playGroup(groupA);
        playGroup(groupB);
        Map<String, MatchDay> fixtures = fixturesByName(stage);
        enter(fixtures.get("HF 1").getId(), HOME_WINS).andExpect(status().isOk());
        enter(fixtures.get("HF 2").getId(), HOME_WINS).andExpect(status().isOk());
        enter(fixturesByName(stage).get("Finale").getId(), HOME_WINS).andExpect(status().isOk());

        enter(fixtures.get("HF 1").getId(), swap(HOME_WINS))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("KNOCKOUT_DOWNSTREAM_PLAYED"));
        // The game for 3rd is still open, so correcting HF 2 isn't blocked by it -- but the final is.
        enter(fixtures.get("HF 2").getId(), swap(HOME_WINS)).andExpect(status().isConflict());
    }

    @Test
    void aSideSetByHand_staysAsSet() throws Exception {
        String stage = createStage("rs-race42-ko");
        MatchDay semi1 = fixturesByName(stage).get("HF 1");
        mockMvc.perform(put("/v1/matchdays/" + semi1.getId() + "/knockout").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"homeSource":{"type":"GROUP_PLACE","groupId":"%s","place":1},
                     "awaySource":{"type":"GROUP_PLACE","groupId":"%s","place":2},
                     "homeTeamId":"%s"}""", groupA, groupB, teams.get("A3"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.homeSetByHand").value(true))
            .andExpect(jsonPath("$.homeSource.groupName").value("Gruppe A"));

        playGroup(groupA);
        playGroup(groupB);
        MatchDay filled = fixturesByName(stage).get("HF 1");
        assertThat(filled.getTeamHome().getId()).isEqualTo(teams.get("A3")); // not A1
        assertThat(filled.getTeamAway().getId()).isEqualTo(teams.get("B2"));
    }

    @Test
    void withRulesThatAllowADraw_theHelperWarns_andALevelResultCantBecomeFinal() throws Exception {
        String response = mockMvc.perform(post("/v1/tiers/" + tierId + "/knockout-stage").contentType(MediaType.APPLICATION_JSON)
                .content(stageBody(null)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(response, "$.warnings")).containsExactly("DRAW_POSSIBLE");
        String stage = JsonPath.read(response, "$.group.id");
        playGroup(groupA);
        playGroup(groupB);

        String semi1 = fixturesByName(stage).get("HF 1").getId();
        int[][] level = {{6, 5}, {12, 10}, {18, 15}, {24, 20}, {30, 25}, {36, 33}, {41, 41}};
        enter(semi1, level).andExpect(status().isOk()).andExpect(jsonPath("$.decided").value(false));
        assertThat(matchDayRepository.findById(semi1).orElseThrow().getResultState()).isEqualTo(ResultState.SUBMITTED);
        mockMvc.perform(post("/v1/matchdays/" + semi1 + "/confirm"))
            .andExpect(status().isConflict());
    }

    @Test
    void theFinalRanking_takesThePlacesOfThePlacingGames_andMarksPromotion() throws Exception {
        setTier("Regionalliga", 1, 3);
        String stage = createStage("rs-race42-ko");
        playGroup(groupA);
        playGroup(groupB);
        playKnockout(stage);

        String ranking = finalRanking();
        assertThat((List<String>) JsonPath.read(ranking, "$[*].teamName")).containsExactly("A1", "B1", "B2", "A2", "A3", "B3");
        assertThat((List<Integer>) JsonPath.read(ranking, "$[*].placeFrom")).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat((List<String>) JsonPath.read(ranking, "$[*].basis")).containsOnly("KNOCKOUT_GAME");
        assertThat((List<Boolean>) JsonPath.read(ranking, "$[*].provisional")).containsOnly(false);
        assertThat((List<String>) JsonPath.read(ranking, "$[?(@.mark == 'PROMOTED')].teamName")).containsExactly("A1", "B1", "B2");
    }

    @Test
    void withoutAGameFor3rd_theSemiFinalLosersShare3to4_andTheRestKeepTheirGroupPlaces() throws Exception {
        String response = mockMvc.perform(post("/v1/tiers/" + tierId + "/knockout-stage").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"sourceGroupIds":["%s","%s"],"qualifiersPerGroup":2,"blueprintId":"rs-race42-ko",
                     "startDate":"2027-03-21T09:00:00Z"}""", groupA, groupB)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String stage = JsonPath.read(response, "$.group.id");
        // Before anything is played: nobody has a place yet.
        assertThat((List<Boolean>) JsonPath.read(finalRanking(), "$[*].provisional")).containsOnly(true);

        playGroup(groupA);
        playGroup(groupB);
        for (String name : List.of("HF 1", "HF 2", "Finale")) {
            enter(fixturesByName(stage).get(name).getId(), HOME_WINS).andExpect(status().isOk());
        }

        String ranking = finalRanking();
        assertThat((List<String>) JsonPath.read(ranking, "$[?(@.basis == 'KNOCKOUT_ROUND')].teamName")).containsExactlyInAnyOrder("B2", "A2");
        assertThat((List<Integer>) JsonPath.read(ranking, "$[?(@.basis == 'KNOCKOUT_ROUND')].placeFrom")).containsOnly(3);
        assertThat((List<Integer>) JsonPath.read(ranking, "$[?(@.basis == 'KNOCKOUT_ROUND')].placeTo")).containsOnly(4);
        assertThat((List<String>) JsonPath.read(ranking, "$[?(@.basis == 'KNOCKOUT_ROUND')].detail")).containsOnly("Halbfinale");
        assertThat((List<String>) JsonPath.read(ranking, "$[?(@.basis == 'GROUP_PLACE')].teamName")).containsExactlyInAnyOrder("A3", "B3");
        assertThat((List<Integer>) JsonPath.read(ranking, "$[?(@.basis == 'GROUP_PLACE')].placeFrom")).containsOnly(5);
        assertThat((List<Integer>) JsonPath.read(ranking, "$[?(@.basis == 'GROUP_PLACE')].placeTo")).containsOnly(6);
    }

    @Test
    void copyForward_startsThePromotedTeamsOneLevelUp() throws Exception {
        setTier("Regionalliga", 2, 2);
        String upperTier = id(mockMvc.perform(post("/v1/tiers").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Bundesliga-Quali\",\"leagueId\":\"%s\",\"level\":1}", leagueId)))
            .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/v1/groups").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Quali\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", upperTier)))
            .andExpect(status().isCreated());
        String stage = createStage("rs-race42-ko");
        playGroup(groupA);
        playGroup(groupB);
        playKnockout(stage);

        String nextSeason = id(mockMvc.perform(post("/v1/seasons").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"2028\",\"federationId\":\"%s\"}", federationId)))
            .andExpect(status().isCreated()).andReturn());
        mockMvc.perform(post("/v1/seasons/" + nextSeason + "/copy-forward").param("from", seasonId))
            .andExpect(status().isOk());

        Map<String, String> groupOfTeam = new java.util.HashMap<>();
        for (TeamParticipation placement : participationRepository.findByLeague_Season_Id(nextSeason)) {
            groupOfTeam.put(placement.getTeam().getName(), placement.getGroup().getName());
        }
        assertThat(groupOfTeam)
            .containsEntry("A1", "Quali").containsEntry("B1", "Quali")
            .containsEntry("B2", "Gruppe B").containsEntry("A2", "Gruppe A");
    }

    // --- helpers ---

    private void setTier(String name, int level, Integer promotionPlaces) throws Exception {
        mockMvc.perform(put("/v1/tiers/" + tierId).contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"%s\",\"leagueId\":\"%s\",\"level\":%d,\"promotionPlaces\":%d}",
                    name, leagueId, level, promotionPlaces)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promotionPlaces").value(promotionPlaces));
    }

    /** Semi-finals and every placing game: the home side wins. */
    private void playKnockout(String stage) throws Exception {
        for (String name : List.of("HF 1", "HF 2", "Finale", "Spiel um Platz 3", "Spiel um Platz 5")) {
            enter(fixturesByName(stage).get(name).getId(), HOME_WINS).andExpect(status().isOk());
        }
    }

    private String finalRanking() throws Exception {
        return mockMvc.perform(get("/v1/tiers/" + tierId + "/final-ranking"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String createStage(String blueprintId) throws Exception {
        String response = mockMvc.perform(post("/v1/tiers/" + tierId + "/knockout-stage").contentType(MediaType.APPLICATION_JSON)
                .content(stageBody(blueprintId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.group.kind").value("KNOCKOUT"))
            .andExpect(jsonPath("$.warnings").isEmpty())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.group.id");
    }

    private String stageBody(String blueprintId) {
        return String.format("""
            {"sourceGroupIds":["%s","%s"],"qualifiersPerGroup":2,"thirdPlaceGame":true,"placementGames":true,
             "blueprintId":%s,"slots":[{"startDate":"2027-03-21T09:00:00Z"},{"startDate":"2027-03-21T12:00:00Z"}]}""",
            groupA, groupB, blueprintId == null ? "null" : "\"" + blueprintId + "\"");
    }

    /** Plays every fixture of the group so that the stronger team (lower number in its name) wins. */
    private void playGroup(String groupId) throws Exception {
        for (MatchDay fixture : matchDayRepository.findByRoundGroupId(groupId)) {
            if (fixture.isBye()) continue;
            boolean homeStronger = fixture.getTeamHome().getName().compareTo(fixture.getTeamAway().getName()) < 0;
            enter(fixture.getId(), homeStronger ? HOME_WINS : swap(HOME_WINS)).andExpect(status().isOk());
        }
    }

    private ResultActions enter(String matchDayId, int[][] runningScores) throws Exception {
        List<Match> segments = matchRepository.findByMatchDay(matchDayRepository.findById(matchDayId).orElseThrow())
            .stream().sorted(Comparator.comparing(Match::getPosition)).toList();
        StringBuilder body = new StringBuilder("{\"matches\":[");
        for (int i = 0; i < runningScores.length; i++) {
            if (i > 0) body.append(',');
            body.append(String.format("{\"matchId\":\"%s\",\"homeScore\":%d,\"awayScore\":%d}",
                segments.get(i).getId(), runningScores[i][0], runningScores[i][1]));
        }
        body.append("]}");
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result")
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private static int[][] swap(int[][] scores) {
        int[][] swapped = new int[scores.length][];
        for (int i = 0; i < scores.length; i++) swapped[i] = new int[] {scores[i][1], scores[i][0]};
        return swapped;
    }

    private Map<String, MatchDay> fixturesByName(String groupId) {
        return matchDayRepository.findByRoundGroupId(groupId).stream()
            .collect(Collectors.toMap(MatchDay::getName, fixture -> fixture));
    }

    private String createGroup(String name) throws Exception {
        return id(mockMvc.perform(post("/v1/groups").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"%s\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", name, tierId)))
            .andExpect(status().isCreated()).andReturn());
    }

    private String newTeam(String name) {
        Club club = new Club();
        club.setName(name + "-Verein-" + tierId);
        club.setFederationId("fed");
        clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        return teamRepository.save(team).getId();
    }

    private String placeTeam(String name, String groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/team-participations").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", newTeam(name), leagueId, groupId)))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.teamId");
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
