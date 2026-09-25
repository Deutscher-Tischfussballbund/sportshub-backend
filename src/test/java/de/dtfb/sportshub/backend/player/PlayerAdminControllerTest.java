package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin-frontend player directory read/write. Player is a single, un-duplicated row (see
 * {@code docs/14-team-player-versioning.md}) -- editing it records a change-history entry per
 * changed field instead of cloning the row.
 */
class PlayerAdminControllerTest extends AuthorizedControllerTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    private String createPlayer(String firstName, String lastName) {
        Player player = new Player();
        player.setBirthYear(1990);
        player.setGender(PlayerGender.MALE);
        player.setFirstName(firstName);
        player.setLastName(lastName);
        player.setNationality("DE");
        return playerRepository.save(player).getId();
    }

    @Test
    void players_listsSeededPlayers() throws Exception {
        mockMvc.perform(get("/v1/admin/players"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void update_rewritesTrackedFields_andRecordsHistory() throws Exception {
        String id = createPlayer("Lukas", "Bauer");

        mockMvc.perform(put("/v1/admin/players/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Lucas", "lastName": "Bauer", "nationality": "AT", "birthYear": 1990,
                     "genderDetail": "male", "active": true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Lucas"))
            .andExpect(jsonPath("$.nationality").value("AT"));

        mockMvc.perform(get("/v1/admin/players/" + id + "/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)) // firstName + nationality changed; lastName didn't
            .andExpect(jsonPath("$[?(@.fieldName == 'firstName')].oldValue").value("Lukas"))
            .andExpect(jsonPath("$[?(@.fieldName == 'firstName')].newValue").value("Lucas"));
    }

    @Test
    void update_missingMandatoryField_isBadRequest_andChangesNothing() throws Exception {
        String id = createPlayer("Mia", "Pflicht");
        String[] incomplete = {
            """
                {"firstName": " ", "lastName": "Pflicht", "birthYear": 1990, "genderDetail": "female", "active": true}""",
            """
                {"firstName": "Mia", "lastName": "", "birthYear": 1990, "genderDetail": "female", "active": true}""",
            """
                {"firstName": "Mia", "lastName": "Pflicht", "genderDetail": "female", "active": true}""",
            """
                {"firstName": "Mia", "lastName": "Pflicht", "birthYear": 1990, "active": true}""",
        };
        for (String body : incomplete) {
            mockMvc.perform(put("/v1/admin/players/" + id).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(get("/v1/admin/players/" + id + "/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void update_unknownPlayer_isNotFound() throws Exception {
        mockMvc.perform(put("/v1/admin/players/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "X", "lastName": "Y", "birthYear": 1990, "genderDetail": "male", "active": true}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_genderDetail_isStored_andPublicGenderIsDerived() throws Exception {
        String id = createPlayer("Kim", "Neumann");

        mockMvc.perform(put("/v1/admin/players/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Kim", "lastName": "Neumann", "nationality": "DE", "birthYear": 1990,
                     "genderDetail": "diverse_men", "active": true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.genderDetail").value("diverse_men"))
            .andExpect(jsonPath("$.gender").value("diverse"));

        mockMvc.perform(get("/v1/admin/players/" + id + "/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.fieldName == 'gender')].newValue").value("DIVERSE_MEN"));
    }

    @Test
    void genderDetail_isVisibleToAnyAdminRole() throws Exception {
        String id = createDiversePlayer();

        for (RequestPostProcessor caller : new RequestPostProcessor[]{
            jwtFor("admin"),
            withRole("gender-region-admin", Role.REGION_ADMIN, ScopeType.REGION, "fed-x"),
            withRole("gender-club-admin", Role.CLUB_ADMIN, ScopeType.CLUB, "club-x"),
            withRole("gender-league-admin", Role.LEAGUE_ADMIN, ScopeType.LEAGUE, "league-x")}) {
            mockMvc.perform(get("/v1/players/" + id).with(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("diverse"))
                .andExpect(jsonPath("$.genderDetail").value("diverse_women"));
        }
    }

    @Test
    void genderDetail_isHiddenFromTeamAdminsAndPlainUsers() throws Exception {
        String id = createDiversePlayer();

        for (RequestPostProcessor caller : new RequestPostProcessor[]{
            withRole("gender-team-admin", Role.TEAM_ADMIN, ScopeType.TEAM, "tid-x"),
            jwtFor("gender-plain-user")}) {
            mockMvc.perform(get("/v1/players/" + id).with(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gender").value("diverse"))
                .andExpect(jsonPath("$.genderDetail").doesNotExist());

            mockMvc.perform(get("/v1/admin/players").param("q", "Divers").with(caller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].gender").value("diverse"))
                .andExpect(jsonPath("$[0].genderDetail").doesNotExist());
        }
    }

    private String createDiversePlayer() {
        Player player = new Player();
        player.setBirthYear(1990);
        player.setFirstName("Divers");
        player.setLastName("Testperson");
        player.setGender(PlayerGender.DIVERSE_WOMEN);
        return playerRepository.save(player).getId();
    }

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    /** Seed a user holding a single grant and return its JWT. Unique dtfb_ids -- see LeagueAuthorizationIntegrationTest. */
    private RequestPostProcessor withRole(String dtfbId, Role role, ScopeType scopeType, String scopeId) {
        User user = userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User created = new User();
            created.setDtfbId(dtfbId);
            return userRepository.save(created);
        });
        if (roleAssignmentRepository.findByUser(user).isEmpty()) {
            RoleAssignment grant = new RoleAssignment();
            grant.setUser(user);
            grant.setRole(role);
            grant.setScopeType(scopeType);
            grant.setScopeId(scopeId);
            grant.setCreatedAt(Instant.now());
            roleAssignmentRepository.save(grant);
        }
        return jwtFor(dtfbId);
    }
}
