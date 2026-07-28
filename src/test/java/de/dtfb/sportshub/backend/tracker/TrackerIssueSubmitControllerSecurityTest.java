package de.dtfb.sportshub.backend.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The bubble's submit endpoint stays on the app's normal Keycloak JWT gate — any authenticated
 * user, no admin/role check — separate from the Basic-Auth-gated {@link TrackerIssueAdminController}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrackerIssueSubmitControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    private static final String BODY = """
        {"title": "Button unreachable", "description": "Tab focus skips the save button"}
        """;

    @Test
    void submit_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/tracker/submit").contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_asAnyAuthenticatedUser_isAllowed() throws Exception {
        mockMvc.perform(post("/v1/tracker/submit").with(jwt().jwt(token -> token
                    .claim("given_name", "Ada").claim("family_name", "Lovelace")))
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Button unreachable"))
            .andExpect(jsonPath("$.reportedBy").value("Ada Lovelace"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.voteCount").value(0));
    }
}
