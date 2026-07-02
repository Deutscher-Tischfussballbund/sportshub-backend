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
 * L1 placement authorization: a {@link de.dtfb.sportshub.backend.teamparticipation.TeamParticipation}
 * belongs to a competition and thus to that competition's region. Create is gated on the target
 * competition's region via {@code @authz.canManageCompetition(#dto.competitionId)}; update/delete on
 * the participation's region via {@code @authz.canManageParticipation(#id)}. Reads stay open.
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * region verdicts are controlled deterministically.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamParticipationControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"teamId": "team-x", "competitionId": "competition-x", "poolId": "pool-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/team-participations").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotRegionManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageCompetition(any())).thenReturn(false);
        mockMvc.perform(post("/v1/team-participations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenRegionManager_passesGate() throws Exception {
        Mockito.when(authz.canManageCompetition(any())).thenReturn(true);
        // Gate passes; the request then fails only because competition "competition-x" does not exist
        // (404), which confirms authorization let it through (a denied request would be 403).
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
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/team-participations").with(jwt()))
            .andExpect(status().isOk());
    }
}
