package de.dtfb.sportshub.backend.team;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier B authorization (org hierarchy — Team): writes require admin of the team's club (or its
 * region / global) via {@code @authz.canManageClub}/{@code canManageTeam}; reads stay open.
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * club/team verdicts are controlled deterministically (the real scope resolution is unit-level).
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/teams").contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Foos Fighters", "clubId": "club-x"}
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotClubManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageClub(any())).thenReturn(false);
        mockMvc.perform(post("/v1/teams").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Foos Fighters", "clubId": "club-x"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenClubManager_passesGate() throws Exception {
        Mockito.when(authz.canManageClub(any())).thenReturn(true);
        // Gate passes; the request then fails only because club "club-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/teams").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Foos Fighters", "clubId": "club-x"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotTeamManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageTeam(any())).thenReturn(false);
        mockMvc.perform(put("/v1/teams/team-x").with(jwt()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Renamed"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/teams").with(jwt()))
            .andExpect(status().isOk());
    }
}
