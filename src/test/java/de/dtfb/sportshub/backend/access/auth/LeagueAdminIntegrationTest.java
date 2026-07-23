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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code competition_organizer} appointed to one league is a full "league admin" scoped to that
 * league -- not just the Tier/Group/MatchDay data covered by {@code canOrganize*} (Tier C), but also
 * the league's own metadata, its placements, and roster confirm/edit -- everything a region admin
 * could do, narrowed to this one league. Exercises the REAL authorization stack (real JWT + role
 * assignment, not mocked) and proves the scoping holds: an organizer of league A gets nothing on
 * league B.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueAdminIntegrationTest {

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
    private static final RequestPostProcessor ORGANIZER = jwtFor("widen-organizer");

    private String leagueAId;
    private String leagueBId;
    private String tierAId;
    private String teamId;
    private String participationAId;
    private String seasonId;
    private String categoryId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = create("/v1/federation", "{\"name\":\"Testverband\"}");
        seasonId = create("/v1/seasons",
            "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\",\"registrationOpensAt\":\"2020-01-01\"}");
        categoryId = create("/v1/category", "{\"name\":\"Herren\",\"shortName\":\"H\"}");

        leagueAId = create("/v1/leagues",
            "{\"name\":\"Liga A\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        leagueBId = create("/v1/leagues",
            "{\"name\":\"Liga B\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        tierAId = create("/v1/tiers", "{\"name\":\"1. Liga\",\"leagueId\":\"" + leagueAId + "\"}");

        teamId = seedTeam(federationId);
        participationAId = create("/v1/team-participations",
            "{\"teamId\":\"" + teamId + "\",\"leagueId\":\"" + leagueAId + "\"}");

        // "organizer" is appointed competition_organizer of league A only.
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(upsertPlayer("widen-organizer"));
        grant.setRole(Role.COMPETITION_ORGANIZER);
        grant.setScopeType(ScopeType.COMPETITION);
        grant.setScopeId(leagueAId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
    }

    @Test
    void organizer_canUpdateOwnLeagueMeta() throws Exception {
        mockMvc.perform(put("/v1/leagues/" + leagueAId).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leagueBody("Liga A renamed")))
            .andExpect(status().isOk());
    }

    @Test
    void organizer_cannotUpdateOtherLeagueMeta() throws Exception {
        mockMvc.perform(put("/v1/leagues/" + leagueBId).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content(leagueBody("Liga B renamed")))
            .andExpect(status().isForbidden());
    }

    private String leagueBody(String name) {
        return "{\"name\":\"" + name + "\",\"seasonId\":\"" + seasonId + "\",\"categoryId\":\"" + categoryId + "\"}";
    }

    @Test
    void organizer_canManageTierInOwnLeague() throws Exception {
        mockMvc.perform(put("/v1/tiers/" + tierAId).with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"1. Liga (umbenannt)\",\"leagueId\":\"" + leagueAId + "\",\"level\":1}"))
            .andExpect(status().isOk());
    }

    @Test
    void organizer_canManageParticipationInOwnLeague() throws Exception {
        mockMvc.perform(post("/v1/team-participations/" + participationAId + "/withdraw").with(ORGANIZER))
            .andExpect(status().isOk());
    }

    @Test
    void organizer_cannotManageParticipationInOtherLeague() throws Exception {
        String otherTeamId = seedTeam("fed-other-unused");
        String otherParticipationId = create("/v1/team-participations",
            "{\"teamId\":\"" + otherTeamId + "\",\"leagueId\":\"" + leagueBId + "\"}");

        mockMvc.perform(post("/v1/team-participations/" + otherParticipationId + "/withdraw").with(ORGANIZER))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizer_canEditRoster() throws Exception {
        mockMvc.perform(post("/v1/team-participations/" + participationAId + "/roster").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": \"player-test\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void organizer_canConfirmRoster() throws Exception {
        mockMvc.perform(post("/v1/team-participations/" + participationAId + "/roster").with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\": \"player-test\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/team-participations/" + participationAId + "/roster/submit").with(ADMIN))
            .andExpect(status().isOk());

        mockMvc.perform(post("/v1/team-participations/" + participationAId + "/roster/confirm").with(ORGANIZER))
            .andExpect(status().isOk());
    }

    @Test
    void organizer_canGrantCoOrganizerForOwnLeague() throws Exception {
        String coOrganizerId = upsertPlayer("widen-co-organizer").getId();
        mockMvc.perform(post("/v1/admin/auth/roles").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + coOrganizerId + "\",\"role\":\"competition_organizer\",\"scopeId\":\""
                    + leagueAId + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void organizer_cannotGrantForOtherLeague() throws Exception {
        String someoneElseId = upsertPlayer("widen-someone-else").getId();
        mockMvc.perform(post("/v1/admin/auth/roles").with(ORGANIZER)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"playerId\":\"" + someoneElseId + "\",\"role\":\"competition_organizer\",\"scopeId\":\""
                    + leagueBId + "\"}"))
            .andExpect(status().isForbidden());
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

    /** Clubs have no create endpoint (they arrive via import) -- seed directly. */
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
