package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.support.TestIds;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerGender;
import de.dtfb.sportshub.backend.player.PlayerRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end (real authorization stack, no mocked {@code @authz}): the DTFB root federation
 * ({@code fed-dtfb}, seeded) gets narrowly-scoped cross-federation authority (see
 * docs/16-root-federation.md) — it may create/manage a root-level (Bundesliga) team for a club
 * belonging to any OTHER (sub-)federation, and that team's roster, but it does NOT gain general
 * authority over that club (profile, membership) or over that club's home federation's own
 * seasons/leagues/tiers/rule sets/roster confirmations.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RootFederationAuthorizationIntegrationTest {

    private static final String ROOT_FEDERATION_ID = "fed-dtfb";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    PlayerRepository playerRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");

    private String subFederationId;
    private String clubId;
    private String rootSeasonId;
    private String rootLeagueId;

    @BeforeEach
    void setup() throws Exception {
        subFederationId = createFederation();
        clubId = createClub(subFederationId);

        String categoryId = create("/v1/categories", "{\"name\":\"Herren\",\"shortName\":\"" + TestIds.unique("H") + "\"}");
        rootSeasonId = create("/v1/seasons",
            "{\"name\":\"Bundesliga-Saison\",\"federationId\":\"" + ROOT_FEDERATION_ID + "\"}");
        rootLeagueId = create("/v1/leagues",
            "{\"name\":\"Bundesliga\",\"seasonId\":\"" + rootSeasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
    }

    @Test
    void dtfbAdmin_mayCreateAndRunARootLevelTeamForAForeignClub() throws Exception {
        RequestPostProcessor dtfbAdmin = grantRegionAdmin("dtfbAdmin1", ROOT_FEDERATION_ID);
        String teamId = createTeamAsDtfbAdmin(dtfbAdmin);

        String participationJson = mockMvc.perform(post("/v1/team-participations").with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teamId\":\"" + teamId + "\",\"leagueId\":\"" + rootLeagueId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String participationId = JsonPath.read(participationJson, "$.id");

        String playerId = seedActiveClubMember();
        mockMvc.perform(post("/v1/team-participations/" + participationId + "/roster").with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}"))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/team-participations/" + participationId + "/roster/submit").with(dtfbAdmin))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/team-participations/" + participationId + "/roster/confirm").with(dtfbAdmin))
            .andExpect(status().isOk());
    }

    @Test
    void dtfbAdmin_mayNotEditTheClubsProfile() throws Exception {
        RequestPostProcessor dtfbAdmin = grantRegionAdmin("dtfbAdmin2", ROOT_FEDERATION_ID);
        mockMvc.perform(put("/v1/admin/clubs/" + clubId).with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Umbenannt\",\"active\":true,\"regionId\":\"" + subFederationId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void dtfbAdmin_mayNotAddAClubMember() throws Exception {
        RequestPostProcessor dtfbAdmin = grantRegionAdmin("dtfbAdmin3", ROOT_FEDERATION_ID);
        String playerId = seedPlayer();
        mockMvc.perform(post("/v1/admin/clubs/" + clubId + "/members").with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void dtfbAdmin_mayNotManageTheSubFederationsOwnSeason() throws Exception {
        RequestPostProcessor dtfbAdmin = grantRegionAdmin("dtfbAdmin4", ROOT_FEDERATION_ID);
        String regionalSeasonId = createRegionalSeason();
        mockMvc.perform(put("/v1/seasons/" + regionalSeasonId).with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Umbenannt\",\"federationId\":\"" + subFederationId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void dtfbAdmin_mayNotManageARegionalTeamOfTheSameClub() throws Exception {
        RequestPostProcessor dtfbAdmin = grantRegionAdmin("dtfbAdmin5", ROOT_FEDERATION_ID);
        String regionalSeasonId = createRegionalSeason();
        String regionalTeamJson = mockMvc.perform(post("/v1/teams").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Regional-Team\",\"clubId\":\"" + clubId + "\",\"seasonId\":\"" + regionalSeasonId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        String regionalTeamId = JsonPath.read(regionalTeamJson, "$.id");

        mockMvc.perform(put("/v1/teams/" + regionalTeamId).with(dtfbAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void outsider_withoutAnyRole_mayNotCreateARootLevelTeam() throws Exception {
        // "outsider" is used as a bare fixture dtfb_id by several test classes that share this
        // suite's H2 context (Spring context caching) -- must stay globally unique or whichever
        // class runs second hits a duplicate-key violation on app_user.dtfb_id.
        userRepository.save(user("outsider-rootfed"));
        mockMvc.perform(post("/v1/teams").with(jwtFor("outsider-rootfed")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"clubId\":\"" + clubId + "\",\"seasonId\":\"" + rootSeasonId + "\"}"))
            .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private String createTeamAsDtfbAdmin(RequestPostProcessor dtfbAdmin) throws Exception {
        String json = mockMvc.perform(post("/v1/teams").with(dtfbAdmin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bundesliga-Team\",\"clubId\":\"" + clubId + "\",\"seasonId\":\"" + rootSeasonId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private String createRegionalSeason() throws Exception {
        return create("/v1/seasons", "{\"name\":\"Regionalsaison\",\"federationId\":\"" + subFederationId + "\"}");
    }

    private String seedPlayer() {
        Player player = new Player();
        player.setBirthYear(1990);
        player.setGender(PlayerGender.MALE);
        player.setFirstName("Test");
        player.setLastName("Spieler");
        return playerRepository.save(player).getId();
    }

    private String seedActiveClubMember() throws Exception {
        String playerId = seedPlayer();
        mockMvc.perform(post("/v1/admin/clubs/" + clubId + "/members").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + playerId + "\"}"))
            .andExpect(status().isCreated());
        return playerId;
    }

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static User user(String dtfbId) {
        User user = new User();
        user.setDtfbId(dtfbId);
        return user;
    }

    private RequestPostProcessor grantRegionAdmin(String dtfbId, String regionId) {
        User user = userRepository.save(user(dtfbId));
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(user);
        grant.setRole(Role.REGION_ADMIN);
        grant.setScopeType(ScopeType.REGION);
        grant.setScopeId(regionId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }

    private String createFederation() throws Exception {
        return create("/v1/federations", "{\"name\":\"Testverband\"}");
    }

    private String createClub(String federationId) throws Exception {
        String body = mockMvc.perform(post("/v1/admin/clubs").with(ADMIN).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Testverein\",\"active\":true,\"regionId\":\"" + federationId + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
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
