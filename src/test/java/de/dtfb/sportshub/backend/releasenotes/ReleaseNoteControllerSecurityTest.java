package de.dtfb.sportshub.backend.releasenotes;

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
 * Writes require a global admin via {@code @PreAuthorize("@authz.isAdmin()")}; reads stay open to
 * any authenticated user -- every logged-in admin-app user sees the "what's new" feed, only
 * global admins author it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ReleaseNoteControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String BODY = """
        {"titleDe": "Neues Feature", "titleEn": "New feature",
         "bodyDe": "Beschreibung", "bodyEn": "Description"}
        """;

    @Test
    void write_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/release-notes").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void write_asNonAdmin_isForbidden() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(false);
        mockMvc.perform(post("/v1/release-notes").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void write_asAdmin_isAllowed() throws Exception {
        Mockito.when(authz.isAdmin()).thenReturn(true);
        mockMvc.perform(post("/v1/release-notes").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.titleDe").value("Neues Feature"))
            .andExpect(jsonPath("$.publishedAt").exists());
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(get("/v1/release-notes").with(jwt()))
            .andExpect(status().isOk());
    }
}
