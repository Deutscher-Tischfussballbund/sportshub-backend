package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.support.TestIds;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier C, end-to-end: exercises the REAL authorization stack (no mocked {@code @authz}) to prove a
 * {@code league_admin} may run the competition under their league, resolving each entity up
 * the spine (Round → Group → Tier → League) to its owning League — and that an outsider, or an
 * organizer of a *different* league, may not. The LEAGUE scope's {@code scopeId} is a league id.
 *
 * <p>The chain is built by the bootstrap admin (dev profile seeds {@code dtfb_id="admin"} as global
 * ADMIN); other actors are seeded as players with a single {@code LEAGUE_ADMIN} grant.
 * Note: creating a {@code Tier} is admin-only ({@code canManageLeague}); the organizer's authority
 * begins at the group and below ({@code canOrganizeTier}/{@code canOrganizeGroup}/…).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueAuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");

    private String leagueId;
    private String tierId;
    private String groupId;
    private String roundId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = create("/v1/seasons", "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        String categoryId = create("/v1/categories", "{\"name\":\"Herren\",\"shortName\":\"" + TestIds.unique("H") + "\"}");
        leagueId = create("/v1/leagues",
            "{\"name\":\"Bundesliga\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        tierId = create("/v1/tiers", "{\"name\":\"1. Bundesliga\",\"leagueId\":\"" + leagueId + "\"}");
        groupId = create("/v1/groups",
            "{\"name\":\"Gruppe A\",\"tierId\":\"" + tierId + "\",\"groupState\":\"READY\"}");
        roundId = create("/v1/rounds", "{\"name\":\"Runde1\",\"index\":1,\"groupId\":\"" + groupId + "\"}");
    }

    @Test
    void leagueOrganizer_mayRunTheCompetitionUnderTheirLeague() throws Exception {
        RequestPostProcessor organizer = grantLeagueOrganizer("organizer", leagueId);

        // Shallow resolution (Group → Tier → League) and progressively deeper walks all resolve to the
        // league the organizer was appointed to.
        mockMvc.perform(post("/v1/groups").with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Gruppe B\",\"tierId\":\"" + tierId + "\",\"groupState\":\"READY\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/rounds").with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Runde2\",\"index\":2,\"groupId\":\"" + groupId + "\"}"))
            .andExpect(status().isCreated());
        // Deep walk: Round → Group → Tier → League.
        mockMvc.perform(put("/v1/rounds/" + roundId).with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Umbenannt\",\"index\":1,\"groupId\":\"" + groupId + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void outsider_withoutAnyRole_isForbidden() throws Exception {
        // "outsider" is used as a bare fixture dtfb_id by several test classes that share this
        // suite's H2 context (Spring context caching) -- must stay globally unique or whichever
        // class runs second hits a duplicate-key violation on app_user.dtfb_id.
        userRepository.save(user("outsider-league"));
        mockMvc.perform(post("/v1/rounds").with(jwtFor("outsider-league")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"index\":9,\"groupId\":\"" + groupId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizerOfADifferentLeague_isForbidden() throws Exception {
        String otherLeagueId = create("/v1/leagues",
            "{\"name\":\"Other\",\"seasonId\":\"" + create("/v1/seasons",
                "{\"name\":\"2026\",\"federationId\":\"" + createFederation() + "\"}")
                + "\",\"categoryId\":\"" + create("/v1/categories", "{\"name\":\"Damen\",\"shortName\":\"" + TestIds.unique("D") + "\"}") + "\"}");
        RequestPostProcessor otherOrganizer = grantLeagueOrganizer("otherOrganizer", otherLeagueId);

        mockMvc.perform(post("/v1/rounds").with(otherOrganizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"index\":9,\"groupId\":\"" + groupId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/rounds").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"index\":9,\"groupId\":\"" + groupId + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static User user(String dtfbId) {
        User user = new User();
        user.setDtfbId(dtfbId);
        return user;
    }

    /** Seed a user with a single LEAGUE_ADMIN grant on {@code leagueId} and return its JWT. */
    private RequestPostProcessor grantLeagueOrganizer(String dtfbId, String leagueId) {
        User user = userRepository.save(user(dtfbId));
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(user);
        grant.setRole(Role.LEAGUE_ADMIN);
        grant.setScopeType(ScopeType.LEAGUE);
        grant.setScopeId(leagueId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }

    private String createFederation() throws Exception {
        return create("/v1/federations", "{\"name\":\"Testverband\"}");
    }

    /** POST as admin and return the created id. */
    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
