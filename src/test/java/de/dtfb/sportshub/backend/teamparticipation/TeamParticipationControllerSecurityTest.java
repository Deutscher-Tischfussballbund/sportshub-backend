package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Authorization for team participations. Create is gated on
 * {@code @authz.canRegisterForLeague(leagueId, teamId)}: passes for a region admin (admin-driven
 * placement) OR a team_admin who represents the team (self-registration). Update/delete are gated
 * on {@code @authz.canManageParticipation(#id)} (region admin only). Reads stay open.
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * verdicts are controlled deterministically.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamParticipationControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"teamId": "team-x", "leagueId": "league-x", "groupId": "group-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/team-participations").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNeitherAdminNorTeamAdmin_isForbidden() throws Exception {
        Mockito.when(authz.canRegisterForLeague(any(), any())).thenReturn(false);
        mockMvc.perform(post("/v1/team-participations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenRegionAdmin_passesGate() throws Exception {
        Mockito.when(authz.canRegisterForLeague(any(), any())).thenReturn(true);
        // Gate passes; the request then fails only because "league-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/team-participations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void create_whenTeamAdmin_passesGate() throws Exception {
        Mockito.when(authz.canRegisterForLeague(any(), any())).thenReturn(true);
        mockMvc.perform(post("/v1/team-participations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotParticipationManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageParticipation(any())).thenReturn(false);
        mockMvc.perform(put("/v1/team-participations/participation-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void delete_whenNotParticipationManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageParticipation(any())).thenReturn(false);
        mockMvc.perform(delete("/v1/team-participations/participation-x").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void withdraw_whenNotParticipationManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageParticipation(any())).thenReturn(false);
        mockMvc.perform(post("/v1/team-participations/participation-x/withdraw").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void withdraw_whenParticipationManager_passesGate() throws Exception {
        Mockito.when(authz.canManageParticipation(any())).thenReturn(true);
        // Gate passes; fails only because "participation-x" does not exist (404), confirming
        // authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/team-participations/participation-x/withdraw").with(jwt()))
            .andExpect(status().isNotFound());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/team-participations").with(jwt()))
            .andExpect(status().isOk());
    }
}
