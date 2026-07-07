package de.dtfb.sportshub.backend.tier;

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
 * A tier belongs to a competition and inherits its region scope — create gated on the target
 * competition's region via {@code @authz.canManageLeague(#tierDto.competitionId)},
 * update/delete on the tier's region via {@code @authz.canManageTier(#id)}. Reads stay open.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TierControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "1. Bayernliga", "leagueId": "league-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/tiers").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotCompetitionManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageLeague(any())).thenReturn(false);
        mockMvc.perform(post("/v1/tiers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenCompetitionManager_passesGate() throws Exception {
        Mockito.when(authz.canManageLeague(any())).thenReturn(true);
        // Gate passes; the request then 404s because competition "league-x" does not exist,
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/tiers").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotTierManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageTier(any())).thenReturn(false);
        mockMvc.perform(put("/v1/tiers/tier-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/tiers").with(jwt()))
            .andExpect(status().isOk());
    }
}
