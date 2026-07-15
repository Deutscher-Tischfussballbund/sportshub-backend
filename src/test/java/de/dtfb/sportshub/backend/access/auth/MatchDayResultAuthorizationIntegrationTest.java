package de.dtfb.sportshub.backend.access.auth;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier D, end-to-end: the league match-day result/confirm workflow is restricted to a representative
 * of a *participating* team (a {@code team_admin} of {@code teamHome}/{@code teamAway}). Exercises
 * the REAL authorization stack — only the affiliation gate is under test; the submitter-vs-opponent
 * rule is the service's job and is checked here too via the "own submission" case.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatchDayResultAuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");
    private static final String EMPTY_RESULT = "{\"matches\":[]}";

    private String matchDayId;
    private String teamHomeId;
    private String teamAwayId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = create("/v1/federation", "{\"name\":\"Testverband\"}");
        String seasonId = create("/v1/seasons", "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        String categoryId = create("/v1/category", "{\"name\":\"Herren\",\"shortName\":\"H\"}");
        String leagueId = create("/v1/leagues",
            "{\"name\":\"Liga\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        String tierId = create("/v1/tiers", "{\"name\":\"1. Liga\",\"leagueId\":\"" + leagueId + "\"}");
        String groupId = create("/v1/groups",
            "{\"name\":\"Gruppe A\",\"tierId\":\"" + tierId + "\",\"groupState\":\"READY\"}");
        String roundId = create("/v1/rounds", "{\"name\":\"Runde1\",\"index\":1,\"groupId\":\"" + groupId + "\"}");
        String locationId = create("/v1/locations", "{\"name\":\"Halle\",\"address\":\"Musterstr 1\"}");

        teamHomeId = seedTeam("Heim");
        teamAwayId = seedTeam("Gast");
        matchDayId = create("/v1/matchdays", String.format(
            "{\"name\":\"Spieltag\",\"roundId\":\"%s\",\"locationId\":\"%s\",\"teamHomeId\":\"%s\",\"teamAwayId\":\"%s\",\"startDate\":\"2025-01-01T00:00:00Z\"}",
            roundId, locationId, teamHomeId, teamAwayId));
    }

    @Test
    void participantSubmits_thenOpponentConfirms() throws Exception {
        RequestPostProcessor home = teamAdmin("home", teamHomeId);
        RequestPostProcessor away = teamAdmin("away", teamAwayId);

        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(home)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_RESULT))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/confirm").with(away))
            .andExpect(status().isOk());
    }

    @Test
    void submitterMayNotConfirmTheirOwnSubmission() throws Exception {
        RequestPostProcessor home = teamAdmin("home", teamHomeId);

        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(home)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_RESULT))
            .andExpect(status().isOk());
        // Passes the affiliation gate (a participant), but the service rejects confirming one's own.
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/confirm").with(home))
            .andExpect(status().isForbidden());
    }

    @Test
    void teamAdminOfNonParticipatingTeam_isForbidden() throws Exception {
        RequestPostProcessor stranger = teamAdmin("stranger", seedTeam("Fremd"));

        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(stranger)
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_RESULT))
            .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedUserWithoutRole_isForbidden() throws Exception {
        upsertPlayer("nobody");
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(jwtFor("nobody"))
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_RESULT))
            .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result")
                .contentType(MediaType.APPLICATION_JSON).content(EMPTY_RESULT))
            .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private Player upsertPlayer(String dtfbId) {
        return playerRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            Player player = new Player();
            player.setDtfbId(dtfbId);
            return playerRepository.save(player);
        });
    }

    /** Seed (or reuse) a player with a TEAM_ADMIN grant on {@code teamId} and return its JWT. */
    private RequestPostProcessor teamAdmin(String dtfbId, String teamId) {
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(upsertPlayer(dtfbId));
        grant.setRole(Role.TEAM_ADMIN);
        grant.setScopeType(ScopeType.TEAM);
        grant.setScopeId(teamId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
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
