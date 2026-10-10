package de.dtfb.sportshub.backend.lineup;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerGender;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.roster.RosterEntry;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Line-ups and substitutions end-to-end (docs/23) on a league from the seeded "Race to 42 (DTFB)"
 * blueprint -- seven games D1 D2 D3 S1 D4 S2 D5, at most 2 games / 1 single per player, 10 players,
 * the block rule, 4 substitutions, line-ups required. Two teams with ten roster players each.
 */
class LineupIntegrationTest extends AuthorizedControllerTest {

    private static final RequestPostProcessor ADMIN = jwt().jwt(token -> token.claim("dtfb_id", "admin"));

    @Autowired ClubRepository clubRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired MatchDayRepository matchDayRepository;
    @Autowired MatchRepository matchRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired RosterEntryRepository rosterEntryRepository;
    @Autowired TeamParticipationRepository participationRepository;
    @Autowired UserRepository userRepository;
    @Autowired RoleAssignmentRepository roleAssignmentRepository;

    private String groupId;
    private MatchDay fixture;
    private List<Match> games;
    /** Ten roster players per side, index 0..9. */
    private List<String> homePlayers;
    private List<String> awayPlayers;
    private RequestPostProcessor homeCaptain;
    private RequestPostProcessor awayCaptain;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = id(mockMvc.perform(post("/v1/seasons").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"2027\",\"federationId\":\"%s\"}", federationId)))
            .andExpect(status().isCreated()).andReturn());
        String leagueId = id(mockMvc.perform(post("/v1/leagues").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Regionalliga\",\"seasonId\":\"%s\",\"categoryId\":\"%s\",\"blueprintId\":\"rs-race42\"}",
                    seasonId, createCategory())))
            .andExpect(status().isCreated()).andReturn());
        String tierId = id(mockMvc.perform(post("/v1/tiers").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Vorrunde\",\"leagueId\":\"%s\"}", leagueId)))
            .andExpect(status().isCreated()).andReturn());
        groupId = id(mockMvc.perform(post("/v1/groups").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Gruppe A\",\"tierId\":\"%s\",\"groupState\":\"READY\"}", tierId)))
            .andExpect(status().isCreated()).andReturn());
        String homeParticipation = placeTeam("H", leagueId);
        String awayParticipation = placeTeam("G", leagueId);
        mockMvc.perform(post("/v1/groups/" + groupId + "/fixtures/generate").contentType(MediaType.APPLICATION_JSON)
                .content("{\"startDate\":\"2099-03-20T09:00:00Z\",\"doubleRoundRobin\":false}"))
            .andExpect(status().isOk());

        fixture = matchDayRepository.findByRoundGroupId(groupId).stream().filter(m -> !m.isBye()).findFirst().orElseThrow();
        games = matchRepository.findByMatchDay(fixture).stream().sorted(Comparator.comparing(Match::getPosition)).toList();
        boolean homeFirst = participationRepository.findById(homeParticipation).orElseThrow().getTeam().getId()
            .equals(fixture.getTeamHome().getId());
        homePlayers = rosterOf(homeFirst ? homeParticipation : awayParticipation);
        awayPlayers = rosterOf(homeFirst ? awayParticipation : homeParticipation);
        homeCaptain = captain("lu-h", fixture.getTeamHome());
        awayCaptain = captain("lu-a", fixture.getTeamAway());
    }

    /** A valid line-up: D1..D3 = P0..P5, S1 = P6, D4 = P0 P1, S2 = P7, D5 = P2 P8 (P9 stays on the bench). */
    private static int[][] valid() {
        return new int[][] {{0, 1}, {2, 3}, {4, 5}, {6}, {0, 1}, {7}, {2, 8}};
    }

    @Test
    void theOpponentSeesALineupOnlyOnceBothAreSubmitted() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());

        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/lineups").with(awayCaptain))
            .andExpect(jsonPath("$.home.submitted").value(true))
            .andExpect(jsonPath("$.home.visible").value(false))
            .andExpect(jsonPath("$.home.planned").isEmpty())
            .andExpect(jsonPath("$.games[0].homePlayers").isEmpty());

        save("AWAY", awayCaptain, awayPlayers, valid(), true).andExpect(status().isOk());
        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/lineups").with(awayCaptain))
            .andExpect(jsonPath("$.bothSubmitted").value(true))
            .andExpect(jsonPath("$.home.visible").value(true))
            .andExpect(jsonPath("$.games[0].homePlayers.length()").value(2))
            .andExpect(jsonPath("$.games[3].awayPlayers.length()").value(1));
    }

    @Test
    void noResultWithoutBothLineups_exceptForANeutralAdmin() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());
        enterFirstSegment(homeCaptain).andExpect(status().isConflict());
        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/result").with(homeCaptain))
            .andExpect(jsonPath("$.lineupRequired").value(true))
            .andExpect(jsonPath("$.lineupsComplete").value(false))
            .andExpect(jsonPath("$.canEdit").value(false));

        enterFirstSegment(ADMIN).andExpect(status().isOk());
    }

    @Test
    void submittingChecksTheRules_draftsDoNot() throws Exception {
        int[][] incomplete = {{0, 1}};
        save("HOME", homeCaptain, homePlayers, incomplete, false).andExpect(status().isOk());
        save("HOME", homeCaptain, homePlayers, incomplete, true).andExpect(status().isBadRequest());

        int[][] threeGames = {{0, 1}, {2, 3}, {4, 5}, {6}, {0, 1}, {7}, {0, 8}}; // P0 in D1, D4, D5
        save("HOME", homeCaptain, homePlayers, threeGames, true).andExpect(status().isBadRequest());

        int[][] twoSingles = {{0, 1}, {2, 3}, {4, 5}, {6}, {0, 1}, {6}, {2, 8}};
        save("HOME", homeCaptain, homePlayers, twoSingles, true).andExpect(status().isBadRequest());

        int[][] repeatInFirstBlock = {{0, 1}, {0, 3}, {4, 5}, {6}, {2, 1}, {7}, {3, 8}};
        save("HOME", homeCaptain, homePlayers, repeatInFirstBlock, true).andExpect(status().isBadRequest());

        save("HOME", homeCaptain, awayPlayers, valid(), true).andExpect(status().isBadRequest()); // not our roster
        save("AWAY", homeCaptain, awayPlayers, valid(), true).andExpect(status().isForbidden()); // not our side
    }

    @Test
    void onceSubmitted_theCaptainCantChangeTheirLineup_evenBeforeTheOpponentSubmits() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), false).andExpect(status().isOk()); // drafts may change
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());

        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/lineups").with(homeCaptain))
            .andExpect(jsonPath("$.home.submitted").value(true))
            .andExpect(jsonPath("$.home.canEdit").value(false));
        save("HOME", homeCaptain, homePlayers, valid(), false).andExpect(status().isConflict());
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isConflict());
        save("HOME", ADMIN, homePlayers, valid(), true).andExpect(status().isOk()); // the tournament management can
    }

    @Test
    void afterBothAreIn_captainsCantEditAnyMore() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());
        save("AWAY", awayCaptain, awayPlayers, valid(), true).andExpect(status().isOk());

        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isConflict());
        save("HOME", ADMIN, homePlayers, valid(), true).andExpect(status().isOk());
    }

    @Test
    void aSubstituteTakesOverThePositionInLaterGames_andShowsOnTheResult() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());
        save("AWAY", awayCaptain, awayPlayers, valid(), true).andExpect(status().isOk());

        // from D3 on, P9 (bench) replaces P4 -- P4 plays only D3 here
        substitute(homeCaptain, "HOME", games.get(2), homePlayers.get(4), homePlayers.get(9)).andExpect(status().isOk());
        // from D4 on, P9 can't come in again; P0 -> P9 is rejected as already used
        substitute(homeCaptain, "HOME", games.get(4), homePlayers.get(0), homePlayers.get(9)).andExpect(status().isBadRequest());
        // from S1 on the single P6 goes out, P4 (substituted out) can't come back
        substitute(homeCaptain, "HOME", games.get(3), homePlayers.get(6), homePlayers.get(4)).andExpect(status().isBadRequest());

        String lineups = mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/lineups").with(homeCaptain))
            .andReturn().getResponse().getContentAsString();
        List<String> d3 = JsonPath.read(lineups, "$.games[2].homePlayers[*].id");
        assertThat(d3).containsExactly(homePlayers.get(9), homePlayers.get(5));
        List<String> d1 = JsonPath.read(lineups, "$.games[0].homePlayers[*].id");
        assertThat(d1).containsExactly(homePlayers.get(0), homePlayers.get(1));
        assertThat((Integer) JsonPath.read(lineups, "$.home.substitutionsLeft")).isEqualTo(3);

        mockMvc.perform(get("/v1/matchdays/" + fixture.getId() + "/result").with(awayCaptain))
            .andExpect(jsonPath("$.games[2].homePlayers[0].name").value("Spieler9 H"))
            .andExpect(jsonPath("$.games[0].awayPlayers.length()").value(2));

        String substitutionId = JsonPath.read(lineups, "$.substitutions[0].id");
        mockMvc.perform(delete("/v1/matchdays/" + fixture.getId() + "/substitutions/" + substitutionId).with(homeCaptain))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.substitutions").isEmpty());
    }

    @Test
    void atMostFourSubstitutions() throws Exception {
        save("HOME", homeCaptain, homePlayers, valid(), true).andExpect(status().isOk());
        save("AWAY", awayCaptain, awayPlayers, valid(), true).andExpect(status().isOk());
        List<String> extra = addToRoster(fixture.getTeamHome(), 4);
        int[] out = {4, 5, 6, 7};
        int[] in = {9, -1, -2, -3};
        substitute(homeCaptain, "HOME", games.get(2), homePlayers.get(out[0]), homePlayers.get(in[0])).andExpect(status().isOk());
        substitute(homeCaptain, "HOME", games.get(2), homePlayers.get(out[1]), extra.get(0)).andExpect(status().isOk());
        substitute(homeCaptain, "HOME", games.get(3), homePlayers.get(out[2]), extra.get(1)).andExpect(status().isOk());
        substitute(homeCaptain, "HOME", games.get(5), homePlayers.get(out[3]), extra.get(2)).andExpect(status().isOk());
        substitute(homeCaptain, "HOME", games.get(6), homePlayers.get(8), extra.get(3)).andExpect(status().isConflict());
    }

    @Test
    void listsCarryTheLineupStatus_missingDraftSubmitted() throws Exception {
        mockMvc.perform(get("/v1/matchdays/" + fixture.getId()))
            .andExpect(jsonPath("$.lineupRequired").value(true))
            .andExpect(jsonPath("$.lineupHome").value("MISSING"))
            .andExpect(jsonPath("$.lineupAway").value("MISSING"));

        save("HOME", homeCaptain, homePlayers, new int[][] {{0, 1}}, false).andExpect(status().isOk());
        save("AWAY", awayCaptain, awayPlayers, valid(), true).andExpect(status().isOk());

        mockMvc.perform(get("/v1/matchdays/" + fixture.getId()))
            .andExpect(jsonPath("$.lineupHome").value("DRAFT"))
            .andExpect(jsonPath("$.lineupAway").value("SUBMITTED"));
        String schedule = mockMvc.perform(get("/v1/groups/" + groupId + "/schedule"))
            .andReturn().getResponse().getContentAsString();
        assertThat((List<String>) JsonPath.read(schedule, "$.rounds[*].fixtures[?(@.id == '" + fixture.getId() + "')].lineupAway"))
            .containsExactly("SUBMITTED");
    }

    // --- helpers ---

    private ResultActions save(String side, RequestPostProcessor who, List<String> roster, int[][] slots, boolean submit) throws Exception {
        StringBuilder body = new StringBuilder("{\"submit\":" + submit + ",\"games\":[");
        for (int i = 0; i < slots.length; i++) {
            if (i > 0) body.append(',');
            List<String> ids = new ArrayList<>();
            for (int p : slots[i]) ids.add("\"" + roster.get(p) + "\"");
            body.append(String.format("{\"matchId\":\"%s\",\"playerIds\":[%s]}", games.get(i).getId(), String.join(",", ids)));
        }
        body.append("]}");
        return mockMvc.perform(put("/v1/matchdays/" + fixture.getId() + "/lineups/" + side).with(who)
            .contentType(MediaType.APPLICATION_JSON).content(body.toString()));
    }

    private ResultActions substitute(RequestPostProcessor who, String side, Match game, String out, String in) throws Exception {
        return mockMvc.perform(post("/v1/matchdays/" + fixture.getId() + "/substitutions").with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"side\":\"%s\",\"matchId\":\"%s\",\"playerOutId\":\"%s\",\"playerInId\":\"%s\"}",
                side, game.getId(), out, in)));
    }

    private ResultActions enterFirstSegment(RequestPostProcessor who) throws Exception {
        return mockMvc.perform(post("/v1/matchdays/" + fixture.getId() + "/result").with(who)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"matches\":[{\"matchId\":\"%s\",\"homeScore\":6,\"awayScore\":4}]}", games.getFirst().getId())));
    }

    /** Ten players on the participation's roster ("Spieler0 H" … "Spieler9 H"). */
    private List<String> rosterOf(String participationId) {
        var participation = participationRepository.findById(participationId).orElseThrow();
        String suffix = participation.getTeam().getName();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ids.add(addPlayer(participation, "Spieler" + i, suffix));
        }
        return ids;
    }

    private List<String> addToRoster(Team team, int count) {
        var participation = participationRepository.findAll().stream()
            .filter(p -> p.getTeam().getId().equals(team.getId())).findFirst().orElseThrow();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(addPlayer(participation, "Extra" + i, team.getName()));
        }
        return ids;
    }

    private String addPlayer(de.dtfb.sportshub.backend.teamparticipation.TeamParticipation participation, String first, String last) {
        Player player = new Player();
        player.setFirstName(first);
        player.setLastName(last);
        player.setBirthYear(1990);
        player.setGender(PlayerGender.FEMALE);
        Player saved = playerRepository.save(player);
        RosterEntry entry = new RosterEntry();
        entry.setParticipation(participation);
        entry.setPlayer(saved);
        entry.setAddedAt(Instant.now());
        rosterEntryRepository.save(entry);
        return saved.getId();
    }

    private RequestPostProcessor captain(String name, Team team) {
        String dtfbId = name + "-" + groupId;
        User user = userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User u = new User();
            u.setDtfbId(dtfbId);
            return userRepository.save(u);
        });
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(user);
        grant.setRole(Role.TEAM_ADMIN);
        grant.setScopeType(ScopeType.TEAM);
        grant.setScopeId(team.getTeamIdentityId());
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private String placeTeam(String name, String leagueId) throws Exception {
        Club club = new Club();
        club.setName(name + "-Verein");
        club.setFederationId("fed");
        clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        String teamId = teamRepository.save(team).getId();
        MvcResult result = mockMvc.perform(post("/v1/team-participations").contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"teamId\":\"%s\",\"leagueId\":\"%s\",\"groupId\":\"%s\"}", teamId, leagueId, groupId)))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
