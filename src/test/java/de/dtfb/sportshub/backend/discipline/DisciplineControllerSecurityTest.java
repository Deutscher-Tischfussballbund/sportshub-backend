package de.dtfb.sportshub.backend.discipline;

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
 * Tier A (revised): a discipline belongs to an event ({@code Discipline.event}), so it inherits that
 * event's region scope — create gated on the target event's region via
 * {@code @authz.canManageEvent(#disciplineDto.eventId)}, update/delete on the discipline's event via
 * {@code @authz.canManageDiscipline(#id)}. Reads stay open. (Its {@code Category} is global config,
 * gated separately.)
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * region verdicts are controlled deterministically (the real scope resolution is unit-level).
 */
@SpringBootTest
@AutoConfigureMockMvc
class DisciplineControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "Offenes Einzel", "eventId": "event-x", "categoryId": "category-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/disciplines").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotEventManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageEvent(any())).thenReturn(false);
        mockMvc.perform(post("/v1/disciplines").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenEventManager_passesGate() throws Exception {
        Mockito.when(authz.canManageEvent(any())).thenReturn(true);
        // Gate passes; the request then fails only because event "event-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/disciplines").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotDisciplineManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageDiscipline(any())).thenReturn(false);
        mockMvc.perform(put("/v1/disciplines/discipline-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/disciplines").with(jwt()))
            .andExpect(status().isOk());
    }
}
