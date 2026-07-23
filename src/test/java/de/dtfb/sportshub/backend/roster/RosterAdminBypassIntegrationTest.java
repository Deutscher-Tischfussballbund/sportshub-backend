package de.dtfb.sportshub.backend.roster;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code registrationOpen} gate on roster edits applies to a self-editing {@code team_admin}
 * only -- an admin above the team (here: global admin) may add/remove/submit regardless, e.g. to
 * fix a copy-forwarded roster before registration opens. Exercises the REAL authorization stack
 * (real JWT + role assignment for the team_admin, not mocked) so both sides of the gate are proven
 * against the actual {@code canConfirmRoster} resolution, not just the service unit logic.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RosterAdminBypassIntegrationTest {

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

    private String participationId;
    private String teamId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = create("/v1/federation", "{\"name\":\"Testverband\"}");
        // no registration window set -- closed, which is the whole point of this test
        String seasonId = create("/v1/seasons",
            "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        String categoryId = create("/v1/category", "{\"name\":\"Herren\",\"shortName\":\"H\"}");
        String leagueId = create("/v1/leagues",
            "{\"name\":\"Liga\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        teamId = seedTeam(federationId);
        participationId = create("/v1/team-participations",
            "{\"teamId\":\"" + teamId + "\",\"leagueId\":\"" + leagueId + "\"}");
    }

    @Test
    void teamAdmin_whenRegistrationClosed_isConflict() throws Exception {
        RequestPostProcessor captain = teamAdmin("captain", teamId);
        add(captain).andExpect(status().isConflict());
    }

    @Test
    void admin_whenRegistrationClosed_isAllowed() throws Exception {
        add(ADMIN).andExpect(status().isCreated());
        mockMvc.perform(post(rosterUrl() + "/submit").with(ADMIN)).andExpect(status().isOk());
    }

    @Test
    void teamAdmin_removePlayer_whenRegistrationClosed_isConflict() throws Exception {
        add(ADMIN).andExpect(status().isCreated()); // admin seeds a player onto the otherwise-locked roster
        RequestPostProcessor captain = teamAdmin("captain", teamId);
        mockMvc.perform(delete(rosterUrl() + "/player-test").with(captain))
            .andExpect(status().isConflict());
    }

    // --- helpers ---

    private String rosterUrl() {
        return "/v1/team-participations/" + participationId + "/roster";
    }

    private ResultActions add(RequestPostProcessor actor) throws Exception {
        return mockMvc.perform(post(rosterUrl()).with(actor)
            .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\": \"player-test\"}"));
    }

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

    /** Clubs have no create endpoint (they arrive via import) -- seed directly, like AuthorizedControllerTest. */
    private String seedTeam(String federationId) {
        Club club = new Club();
        club.setName("Testverein");
        club.setFederationId(federationId);
        clubRepository.save(club);
        Team team = new Team();
        team.setName("Team");
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
