package de.dtfb.sportshub.backend.access.apiclient;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unlike most admin CRUD in this app, reads are ALSO global-admin-only here -- this data controls
 * write access for machine clients, not day-to-day domain content (see ApiClientGrantController).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApiClientGrantControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"clientId": "dtfb-example", "name": "Example Service", "writeAccess": true, "scopeType": "global"}
        """;

    @Test
    void read_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/v1/admin/api-clients"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void read_asNonAdmin_isForbidden() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(false);
        mockMvc.perform(get("/v1/admin/api-clients").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void read_asAdmin_isAllowed() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(true);
        mockMvc.perform(get("/v1/admin/api-clients").with(jwt()))
            .andExpect(status().isOk());
    }

    @Test
    void write_asNonAdmin_isForbidden() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(false);
        mockMvc.perform(post("/v1/admin/api-clients").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void write_asAdmin_isAllowed() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(true);
        mockMvc.perform(post("/v1/admin/api-clients").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId").value("dtfb-example"))
            .andExpect(jsonPath("$.writeAccess").value(true))
            .andExpect(jsonPath("$.createdAt").exists());
    }
}
