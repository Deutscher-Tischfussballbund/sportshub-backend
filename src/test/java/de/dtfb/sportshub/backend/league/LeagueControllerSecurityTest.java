package de.dtfb.sportshub.backend.league;

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
 * A league belongs to a season and inherits its region scope — create gated on the target season's
 * region via {@code @authz.canManageSeason(#leagueDto.seasonId)}, update/delete on the league's
 * region via {@code @authz.canManageLeague(#id)}. Reads stay open.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "Bayernliga", "seasonId": "season-x", "categoryId": "category-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/leagues").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotSeasonManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(false);
        mockMvc.perform(post("/v1/leagues").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenSeasonManager_passesGate() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(true);
        // Gate passes; the request then 404s because season "season-x" does not exist,
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/leagues").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotLeagueManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageLeague(any())).thenReturn(false);
        mockMvc.perform(put("/v1/leagues/league-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/leagues").with(jwt()))
            .andExpect(status().isOk());
    }
}
