package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Copy-forward is gated on the target season's region via {@code @authz.canManageSeason(#targetSeasonId)}
 * — a region admin seeds their own new season. A mock JWT satisfies the {@code authenticated()}
 * baseline; {@code @authz} is mocked to control the verdict deterministically.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CopyForwardControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    @Test
    void copyForward_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/seasons/target-x/copy-forward").param("from", "source-x"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void copyForward_whenNotSeasonManager_isForbidden() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(false);
        mockMvc.perform(post("/v1/seasons/target-x/copy-forward").with(jwt()).param("from", "source-x"))
            .andExpect(status().isForbidden());
    }

    @Test
    void copyForward_whenSeasonManager_passesGate() throws Exception {
        Mockito.when(authz.canManageSeason(any())).thenReturn(true);
        // Gate passes; the request then fails only because target "target-x" does not exist (404),
        // which confirms authorization let it through (a denied request would be 403).
        mockMvc.perform(post("/v1/seasons/target-x/copy-forward").with(jwt()).param("from", "source-x"))
            .andExpect(status().isNotFound());
    }
}
