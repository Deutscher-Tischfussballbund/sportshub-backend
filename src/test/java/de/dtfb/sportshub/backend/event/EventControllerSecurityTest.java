package de.dtfb.sportshub.backend.event;

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
 * Tier A (revised): an event belongs to a region via Event → Season → Federation, so its writes
 * require admin of that region (or global) — create gated on the target season's region via
 * {@code @authz.canManageSeason(#eventDto.seasonId)}, update/delete on the event's region via
 * {@code @authz.canManageEvent(#id)}. Reads stay open.
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * region verdicts are controlled deterministically (the real scope resolution is unit-level).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EventControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "Bundesliga 2024", "seasonId": "season-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/events").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotSeasonManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(false);
        mockMvc.perform(post("/v1/events").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenSeasonManager_passesGate() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(true);
        // Gate passes; the request then fails only because season "season-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/events").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotEventManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageEvent(any())).thenReturn(false);
        mockMvc.perform(put("/v1/events/event-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/events").with(jwt()))
            .andExpect(status().isOk());
    }
}
