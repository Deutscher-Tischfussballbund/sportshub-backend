package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.support.TestIds;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerGender;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.roster.RosterEntry;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Result entry and confirmation end-to-end through the REAL authorization stack (docs/17, SPO-15):
 * every team member of either side (roster player with a login, captain, admin above the team) may
 * enter or edit; only captains confirm, each for their own side, as a dedicated action (entering never
 * confirms); the result is final once both captains confirmed the current, decided version; a neutral admin (league/federation/global) finalizes at
 * once and is the only one who may change a final result. Standings are recomputed on every
 * finalization, so a correction replaces the old result.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatchDayResultAuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    LeagueRepository leagueRepository;

    @Autowired
    TeamParticipationRepository participationRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    RosterEntryRepository rosterEntryRepository;

    @Autowired
    de.dtfb.sportshub.backend.group.GroupRepository groupRepository;

    @Autowired
    de.dtfb.sportshub.backend.standing.StandingRepository standingRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");

    private String leagueId;
    private String groupId;
    private String matchDayId;
    private String matchId;
    private String teamHomeId;
    private String teamAwayId;
    private String homeParticipationId;
    private String awayParticipationId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = create("/v1/federations", "{\"name\":\"Testverband\"}");
        String seasonId = create("/v1/seasons", "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        String categoryId = create("/v1/categories", "{\"name\":\"Herren\",\"shortName\":\"" + TestIds.unique("H") + "\"}");
        leagueId = create("/v1/leagues",
            "{\"name\":\"Liga\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        de.dtfb.sportshub.backend.support.LineupTestSupport.disableLineups(mockMvc, ADMIN, leagueId); // this class tests result rules, docs/23 has its own
        String tierId = create("/v1/tiers", "{\"name\":\"1. Liga\",\"leagueId\":\"" + leagueId + "\"}");
        groupId = create("/v1/groups",
            "{\"name\":\"Gruppe A\",\"tierId\":\"" + tierId + "\",\"groupState\":\"READY\"}");
        String roundId = create("/v1/rounds", "{\"name\":\"Runde1\",\"index\":1,\"groupId\":\"" + groupId + "\"}");
        String locationId = create("/v1/locations", "{\"name\":\"Halle\",\"address\":\"Musterstr 1\"}");

        teamHomeId = seedTeam("Heim");
        teamAwayId = seedTeam("Gast");
        homeParticipationId = participate(teamHomeId);
        awayParticipationId = participate(teamAwayId);
        matchDayId = create("/v1/matchdays", String.format(
            "{\"name\":\"Spieltag\",\"roundId\":\"%s\",\"locationId\":\"%s\",\"teamHomeId\":\"%s\",\"teamAwayId\":\"%s\",\"startDate\":\"2025-01-01T00:00:00Z\"}",
            roundId, locationId, teamHomeId, teamAwayId));
        matchId = create("/v1/matches", "{\"matchDayId\":\"" + matchDayId + "\","
            + "\"startTime\":\"2025-01-01T10:00:00Z\",\"type\":\"SINGLE\"}");
    }

    // --- the normal cycle ---

    @Test
    void captainEnters_thenBothCaptainsConfirm_resultIsFinal() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"))
            .andExpect(jsonPath("$.homeAgreedAt").isEmpty()) // entering never confirms
            .andExpect(jsonPath("$.awayAgreedAt").isEmpty());

        confirm(captain("cap-a", teamAwayId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"));
        confirm(captain("cap-h", teamHomeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
        assertThat(standingField(teamHomeId, "wins")).isEqualTo(1);
        assertThat(standingField(teamAwayId, "losses")).isEqualTo(1);
    }

    @Test
    void rosterPlayerEnters_thenBothCaptainsHaveToConfirm() throws Exception {
        enter(rosterPlayer("pl-h", homeParticipationId), 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"))
            .andExpect(jsonPath("$.homeAgreedAt").isEmpty())
            .andExpect(jsonPath("$.awayAgreedAt").isEmpty());

        confirm(captain("cap-a", teamAwayId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"));
        confirm(captain("cap-h", teamHomeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void anEditCancelsAllConfirmations() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId)).andExpect(status().isOk());

        enter(rosterPlayer("pl-a", awayParticipationId), 2, 5)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"))
            .andExpect(jsonPath("$.homeAgreedAt").isEmpty())
            .andExpect(jsonPath("$.awayAgreedAt").isEmpty())
            .andExpect(jsonPath("$.games[0].homeScore").value(2));
    }

    // --- who may confirm ---

    @Test
    void aRosterPlayerWhoIsNotCaptain_mayNotConfirm() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());

        confirm(rosterPlayer("pl-a", awayParticipationId)).andExpect(status().isForbidden());
    }

    @Test
    void aCaptainWhoseSideAlreadyAgreed_cannotConfirmAgain() throws Exception {
        RequestPostProcessor home = captain("cap-h", teamHomeId);
        enter(home, 5, 2).andExpect(status().isOk());
        confirm(home).andExpect(status().isOk());

        confirm(home).andExpect(status().isConflict());
    }

    @Test
    void aClubAdmin_entersForTheirTeam_butMayNotConfirm() throws Exception {
        RequestPostProcessor clubAdmin = clubAdmin("club-h", teamHomeId);
        enter(clubAdmin, 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"));

        confirm(clubAdmin).andExpect(status().isForbidden());
    }

    // --- neutral admins and final results ---

    @Test
    void aLeagueAdminsEntry_isFinalAtOnce() throws Exception {
        enter(leagueAdmin("liga"), 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void aNeutralAdmin_confirmsAStalledResult() throws Exception {
        enter(rosterPlayer("pl-h", homeParticipationId), 5, 2).andExpect(status().isOk());

        confirm(leagueAdmin("liga"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void aFinalResult_isFrozenForTeams_andAnAdminCorrectionReplacesItInTheStandings() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());
        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId)).andExpect(status().isOk());

        enter(captain("cap-h", teamHomeId), 6, 2).andExpect(status().isConflict());

        enter(ADMIN, 2, 5)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
        assertThat(standingField(teamHomeId, "played")).isEqualTo(1); // replaced, not counted twice
        assertThat(standingField(teamHomeId, "losses")).isEqualTo(1);
        assertThat(standingField(teamAwayId, "wins")).isEqualTo(1);
    }

    @Test
    void aResultThatWasFinal_staysWithTheAdmins_evenWhenACorrectionReopensIt() throws Exception {
        String second = addGame("DOUBLE");
        String third = addGame("SINGLE");
        setLeagueDecision("FIRST_TO", 2);
        enterGames(captain("cap-h", teamHomeId), matchId, 5, 2, second, 5, 4).andExpect(status().isOk());
        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId)).andExpect(jsonPath("$.resultState").value("CONFIRMED"));

        // The admin's correction leaves it 1 : 1 -- undecided, pending again, but not the teams' any more.
        enterGames(ADMIN, second, 4, 5)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED"))
            .andExpect(jsonPath("$.decided").value(false));
        mockMvc.perform(get("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId)))
            .andExpect(jsonPath("$.adminOnly").value(true))
            .andExpect(jsonPath("$.canEdit").value(false))
            .andExpect(jsonPath("$.canConfirm").value(false));
        enterGames(captain("cap-h", teamHomeId), third, 5, 3).andExpect(status().isConflict());
        enterGames(rosterPlayer("pl-a", awayParticipationId), third, 3, 5).andExpect(status().isConflict());
        mockMvc.perform(get("/v1/matchdays/pending-results").with(captain("cap-h", teamHomeId)))
            .andExpect(jsonPath("$.length()").value(0));

        enterGames(ADMIN, third, 5, 3)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
        confirm(captain("cap-a", teamAwayId)).andExpect(status().isConflict());
    }

    // --- who may not act at all ---

    @Test
    void aPlayerOnBothRosters_mayNotEnter() throws Exception {
        RequestPostProcessor both = rosterPlayer("pl-both", homeParticipationId);
        addToRoster("pl-both", awayParticipationId);

        enter(both, 5, 2).andExpect(status().isForbidden());
    }

    @Test
    void captainOfANonParticipatingTeam_isForbidden() throws Exception {
        enter(captain("stranger", seedTeam("Fremd")), 5, 2).andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserWithoutRole_isForbidden() throws Exception {
        upsertUser("nobody");
        enter(jwtFor("nobody"), 5, 2).andExpect(status().isForbidden());
        confirm(jwtFor("nobody")).andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result")
                .contentType(MediaType.APPLICATION_JSON).content(resultBody(5, 2)))
            .andExpect(status().isUnauthorized());
    }

    // --- only a decided fixture can become final (rule set's matchday decision) ---

    @Test
    void aPartialResult_cannotBecomeFinal_neitherByTheCaptainsNorByAnAdmin() throws Exception {
        addGame("DOUBLE");
        addGame("SINGLE"); // 3 games, no decision set -> all games count
        enter(captain("cap-h", teamHomeId), 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamesEntered").value(1))
            .andExpect(jsonPath("$.gamesTotal").value(3))
            .andExpect(jsonPath("$.decided").value(false));

        mockMvc.perform(get("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-a", teamAwayId)))
            .andExpect(jsonPath("$.canConfirm").value(false));
        confirm(captain("cap-a", teamAwayId)).andExpect(status().isConflict());
        confirm(ADMIN).andExpect(status().isConflict());
        enter(ADMIN, 5, 2)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("SUBMITTED")); // an admin's partial entry isn't final
    }

    @Test
    void allGamesEntered_theResultCanBecomeFinal() throws Exception {
        String second = addGame("DOUBLE");
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matches\":[{\"matchId\":\"" + matchId + "\",\"homeScore\":5,\"awayScore\":2},"
                    + "{\"matchId\":\"" + second + "\",\"homeScore\":3,\"awayScore\":5}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decided").value(true));

        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void firstToTwo_isDecidedWithAGameLeftUnplayed() throws Exception {
        String second = addGame("DOUBLE");
        addGame("SINGLE");
        setLeagueDecision("FIRST_TO", 2);
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matches\":[{\"matchId\":\"" + matchId + "\",\"homeScore\":5,\"awayScore\":2},"
                    + "{\"matchId\":\"" + second + "\",\"homeScore\":5,\"awayScore\":4}]}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gamesEntered").value(2))
            .andExpect(jsonPath("$.decided").value(true));

        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resultState").value("CONFIRMED"));
    }

    @Test
    void gameBased_theListScoreIsGamesWon() throws Exception {
        String second = addGame("DOUBLE");
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matches\":[{\"matchId\":\"" + matchId + "\",\"homeScore\":5,\"awayScore\":2},"
                    + "{\"matchId\":\"" + second + "\",\"homeScore\":5,\"awayScore\":4}]}"))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v1/matchdays/" + matchDayId).with(ADMIN))
            .andExpect(jsonPath("$.scoreHome").value(2))
            .andExpect(jsonPath("$.scoreAway").value(0))
            .andExpect(jsonPath("$.gamesEntered").value(2));
    }

    // --- live table ---

    @Test
    void theLiveTable_countsAnEnteredResult_markedProvisional_whileTheOfficialOneDoesNot() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());

        assertThat((List<?>) JsonPath.read(standings(false), "$")).isEmpty();
        String live = standings(true);
        assertThat((List<Integer>) JsonPath.read(live, "$[?(@.teamId == '" + teamHomeId + "')].wins")).containsExactly(1);
        assertThat((List<Boolean>) JsonPath.read(live, "$[*].provisional")).containsOnly(true);

        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId)).andExpect(status().isOk());

        assertThat((List<Boolean>) JsonPath.read(standings(true), "$[*].provisional")).containsOnly(false);
        assertThat(standingField(teamHomeId, "wins")).isEqualTo(1);
    }

    @Test
    void theLiveTable_followsAnEdit() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());
        enter(captain("cap-a", teamAwayId), 2, 5).andExpect(status().isOk());

        String live = standings(true);
        assertThat((List<Integer>) JsonPath.read(live, "$[?(@.teamId == '" + teamAwayId + "')].wins")).containsExactly(1);
        assertThat((List<Integer>) JsonPath.read(live, "$[?(@.teamId == '" + teamHomeId + "')].played")).containsExactly(1);
    }

    @Test
    void placedTeamsWithoutAPlayedFixture_appearInTheTables_butAreNotStored() throws Exception {
        placeInGroup(homeParticipationId);
        placeInGroup(awayParticipationId);
        String newcomer = seedTeam("Neu");
        placeInGroup(participate(newcomer));
        String withdrawn = seedTeam("Zurueckgezogen");
        TeamParticipation gone = participationRepository.findById(placeInGroup(participate(withdrawn))).orElseThrow();
        gone.setStatus(de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus.WITHDRAWN);
        participationRepository.save(gone);

        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());
        confirm(captain("cap-a", teamAwayId)).andExpect(status().isOk());
        confirm(captain("cap-h", teamHomeId)).andExpect(status().isOk());

        for (boolean live : new boolean[] {false, true}) {
            String table = standings(live);
            assertThat((List<String>) JsonPath.read(table, "$[*].teamId")).containsExactly(teamHomeId, newcomer, teamAwayId);
            assertThat((List<Integer>) JsonPath.read(table, "$[?(@.teamId == '" + newcomer + "')].played")).containsExactly(0);
            assertThat((List<Integer>) JsonPath.read(table, "$[?(@.teamId == '" + newcomer + "')].points")).containsExactly(0);
        }
        // Stored rows mean "has recorded results" to the delete guards -- only teams that played.
        assertThat(standingRepository.findByGroupOrderByPointsDescSetsWonDesc(groupRepository.findById(groupId).orElseThrow()))
            .extracting(st -> st.getTeam().getId()).containsExactlyInAnyOrder(teamHomeId, teamAwayId);
    }

    // --- request checks and the read side ---

    @Test
    void anEmptyResult_isRejected() throws Exception {
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId))
                .contentType(MediaType.APPLICATION_JSON).content("{\"matches\":[]}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void theResultScreen_tellsEachUserWhatTheyMayDo() throws Exception {
        enter(captain("cap-h", teamHomeId), 5, 2).andExpect(status().isOk());

        mockMvc.perform(get("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-a", teamAwayId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.side").value("AWAY"))
            .andExpect(jsonPath("$.canEdit").value(true))
            .andExpect(jsonPath("$.canConfirm").value(true))
            .andExpect(jsonPath("$.neutralAdmin").value(false));
        mockMvc.perform(get("/v1/matchdays/" + matchDayId + "/result").with(captain("cap-h", teamHomeId)))
            .andExpect(jsonPath("$.canConfirm").value(true)); // entering didn't confirm
        mockMvc.perform(get("/v1/matchdays/" + matchDayId + "/result").with(rosterPlayer("pl-a", awayParticipationId)))
            .andExpect(jsonPath("$.canEdit").value(true))
            .andExpect(jsonPath("$.canConfirm").value(false));
    }

    // --- helpers ---

    private ResultActions enter(RequestPostProcessor who, int home, int away) throws Exception {
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(who)
            .contentType(MediaType.APPLICATION_JSON).content(resultBody(home, away)));
    }

    /** Scores for several games: matchId, home, away, matchId, home, away, ... */
    private ResultActions enterGames(RequestPostProcessor who, Object... idHomeAway) throws Exception {
        StringBuilder body = new StringBuilder("{\"matches\":[");
        for (int i = 0; i < idHomeAway.length; i += 3) {
            body.append(i == 0 ? "" : ",").append("{\"matchId\":\"").append(idHomeAway[i])
                .append("\",\"homeScore\":").append(idHomeAway[i + 1])
                .append(",\"awayScore\":").append(idHomeAway[i + 2]).append('}');
        }
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(who)
            .contentType(MediaType.APPLICATION_JSON).content(body.append("]}").toString()));
    }

    private ResultActions confirm(RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/confirm").with(who));
    }

    private String resultBody(int home, int away) {
        return "{\"matches\":[{\"matchId\":\"" + matchId + "\",\"homeScore\":" + home + ",\"awayScore\":" + away + "}]}";
    }

    private int standingField(String teamId, String field) throws Exception {
        String json = mockMvc.perform(get("/v1/groups/" + groupId + "/standings").with(ADMIN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> values = JsonPath.read(json, "$[?(@.teamId == '" + teamId + "')]." + field);
        assertThat(values).hasSize(1);
        return values.getFirst();
    }

    private String addGame(String type) throws Exception {
        return create("/v1/matches", "{\"matchDayId\":\"" + matchDayId + "\","
            + "\"startTime\":\"2025-01-01T10:00:00Z\",\"type\":\"" + type + "\"}");
    }

    /** Sets the matchday decision on the league's own rules (the group's effective rule set). */
    private void setLeagueDecision(String decision, int target) throws Exception {
        String leagueJson = mockMvc.perform(get("/v1/leagues/" + leagueId).with(ADMIN))
            .andReturn().getResponse().getContentAsString();
        String ruleSetId = JsonPath.read(leagueJson, "$.ruleSetId");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/v1/league-rule-sets/" + ruleSetId)
                .with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Liga-Regeln\",\"lineupRequired\":false,\"matchdayDecision\":\"" + decision + "\",\"matchdayTarget\":" + target + "}"))
            .andExpect(status().isOk());
    }

    private String standings(boolean provisional) throws Exception {
        return mockMvc.perform(get("/v1/groups/" + groupId + "/standings?provisional=" + provisional).with(ADMIN))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private User upsertUser(String dtfbId) {
        return userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User user = new User();
            user.setDtfbId(dtfbId);
            return userRepository.save(user);
        });
    }

    private void grant(String dtfbId, Role role, ScopeType scopeType, String scopeId) {
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(upsertUser(dtfbId));
        grant.setRole(role);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
    }

    /** A captain: {@code TEAM_ADMIN} on the team's identity. dtfb_ids are made unique per test. */
    private RequestPostProcessor captain(String name, String teamId) {
        String dtfbId = scoped(name);
        if (roleAssignmentRepository.findByUser(upsertUser(dtfbId)).isEmpty()) {
            grant(dtfbId, Role.TEAM_ADMIN, ScopeType.TEAM, teamRepository.findById(teamId).orElseThrow().getTeamIdentityId());
        }
        return jwtFor(dtfbId);
    }

    private RequestPostProcessor clubAdmin(String name, String teamId) {
        String dtfbId = scoped(name);
        grant(dtfbId, Role.CLUB_ADMIN, ScopeType.CLUB, teamRepository.findById(teamId).orElseThrow().getClub().getId());
        return jwtFor(dtfbId);
    }

    private RequestPostProcessor leagueAdmin(String name) {
        String dtfbId = scoped(name);
        League league = leagueRepository.findById(leagueId).orElseThrow();
        grant(dtfbId, Role.LEAGUE_ADMIN, ScopeType.LEAGUE, league.getLeagueIdentityId());
        return jwtFor(dtfbId);
    }

    /** A roster player with a login and no role: a Player linked to the user, on the participation's roster. */
    private RequestPostProcessor rosterPlayer(String name, String participationId) {
        addToRoster(name, participationId);
        return jwtFor(scoped(name));
    }

    private void addToRoster(String name, String participationId) {
        User user = upsertUser(scoped(name));
        Player player = playerRepository.findAll().stream()
            .filter(p -> p.getUser() != null && p.getUser().getId().equals(user.getId()))
            .findFirst()
            .orElseGet(() -> {
                Player p = new Player();
                p.setUser(user);
                p.setFirstName(name);
                p.setLastName("Test");
                p.setBirthYear(1990);
                p.setGender(PlayerGender.MALE);
                return playerRepository.save(p);
            });
        RosterEntry entry = new RosterEntry();
        entry.setParticipation(participationRepository.findById(participationId).orElseThrow());
        entry.setPlayer(player);
        entry.setAddedAt(Instant.now());
        rosterEntryRepository.save(entry);
    }

    /** The DB is shared across test methods, so every user is scoped to this test's fixture. */
    private String scoped(String name) {
        return name + "-" + matchDayId;
    }

    private String participate(String teamId) {
        TeamParticipation participation = new TeamParticipation();
        participation.setTeam(teamRepository.findById(teamId).orElseThrow());
        participation.setLeague(leagueRepository.findById(leagueId).orElseThrow());
        return participationRepository.save(participation).getId();
    }

    private String placeInGroup(String participationId) {
        TeamParticipation participation = participationRepository.findById(participationId).orElseThrow();
        participation.setGroup(groupRepository.findById(groupId).orElseThrow());
        return participationRepository.save(participation).getId();
    }

    private String seedTeam(String name) {
        Club club = new Club();
        club.setName(name + "-Verein");
        club.setFederationId("fed");
        clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        return teamRepository.save(team).getId();
    }

    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
