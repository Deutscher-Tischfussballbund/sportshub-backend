package de.dtfb.sportshub.backend.leaguerules;

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
 * A league rule set is owned by a region — create gated on the target region via
 * {@code @authz.canManageRuleSet(#ruleSetDto.federationId)}, update/delete on the rule set's
 * region via {@code @authz.canManageRuleSetById(#id)}. Reads stay open.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueRuleSetControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "Standard", "federationId": "federation-x", "playSystem": "ROUND_ROBIN"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/league-rule-sets").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotRegionManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageRuleSet(any())).thenReturn(false);
        mockMvc.perform(post("/v1/league-rule-sets").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenRegionManager_passesGate() throws Exception {
        Mockito.when(authz.canManageRuleSet(any())).thenReturn(true);
        // Gate passes; the request then 404s because federation "federation-x" does not exist,
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/league-rule-sets").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotRuleSetManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageRuleSetById(any())).thenReturn(false);
        mockMvc.perform(put("/v1/league-rule-sets/rule-set-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/league-rule-sets").with(jwt()))
            .andExpect(status().isOk());
    }
}
