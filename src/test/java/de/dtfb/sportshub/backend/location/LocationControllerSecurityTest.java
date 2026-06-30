package de.dtfb.sportshub.backend.location;

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
 * Tier A (revised): a location belongs to a region ({@code Location.federation}), so its writes
 * require admin of that region (or global) — create gated on the target region via
 * {@code @authz.canManageRegion(#locationDto.federationId)}, update/delete on the location's region
 * via {@code @authz.canManageLocation(#id)}. A region-less location is global (admin-only). Reads
 * stay open.
 *
 * <p>A mock JWT satisfies the {@code authenticated()} baseline; {@code @authz} is mocked so the
 * region verdicts are controlled deterministically (the real scope resolution is unit-level).
 */
@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"name": "MKK", "address": "Flensburg, Musterstraße 1", "federationId": "region-x"}
        """;

    @Test
    void create_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/locations").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void create_whenNotRegionManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageRegion(any())).thenReturn(false);
        mockMvc.perform(post("/v1/locations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void create_whenRegionManager_passesGate() throws Exception {
        Mockito.when(authz.canManageRegion(any())).thenReturn(true);
        // Gate passes; the request then fails only because region "region-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/locations").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound());
    }

    @Test
    void update_whenNotLocationManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageLocation(any())).thenReturn(false);
        mockMvc.perform(put("/v1/locations/location-x").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/locations").with(jwt()))
            .andExpect(status().isOk());
    }
}
