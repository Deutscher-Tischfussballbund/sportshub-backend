package de.dtfb.sportshub.backend.tracker;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The plain tracker page's API is public — list/create/vote need no login at all — except
 * convert/delete, which require a GitHub login (gated to one org at login time by
 * {@link GitHubOrgOAuth2UserService}, not re-checked per request; {@code isAuthenticated()} is
 * enough here). {@code oauth2Login()} stands in for a real GitHub session.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TrackerIssueAdminControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    GitHubIssueClient gitHubIssueClient;

    @Test
    void list_needsNoAuth() throws Exception {
        mockMvc.perform(get("/v1/tracker/issues"))
            .andExpect(status().isOk());
    }

    @Test
    void convert_withoutLogin_isForbidden() throws Exception {
        mockMvc.perform(post("/v1/tracker/issues/whatever/convert")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"githubRepo": "dtfb/sportshub-backend"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void delete_withoutLogin_isForbidden() throws Exception {
        mockMvc.perform(delete("/v1/tracker/issues/whatever"))
            .andExpect(status().isForbidden());
    }

    @Test
    void fullFlow_createVoteConvertDelete() throws Exception {
        MvcResult created = mockMvc.perform(post("/v1/tracker/issues")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"title": "Sidebar overlaps content", "description": "On narrow viewports", "reportedBy": "Sam"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.voteCount").value(0))
            .andReturn();
        String id = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/v1/tracker/issues/" + id + "/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"voterName": "Sam"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voteCount").value(1));

        // Voting again with the same name is idempotent — no double count.
        mockMvc.perform(post("/v1/tracker/issues/" + id + "/votes")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"voterName": "Sam"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voteCount").value(1));

        mockMvc.perform(get("/v1/tracker/issues?voterName=Sam"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].votedByMe").value(true));

        mockMvc.perform(delete("/v1/tracker/issues/" + id + "/votes?voterName=Sam"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.voteCount").value(0))
            .andExpect(jsonPath("$.votedByMe").value(false));

        // The service appends a footer to the description before calling out, so match loosely.
        // Conversion now uses the logged-in user's own OAuth2 access token, not a server-held one.
        Mockito.when(gitHubIssueClient.createIssue(
                Mockito.eq("dtfb/sportshub-backend"), Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
            .thenReturn("https://github.com/dtfb/sportshub-backend/issues/1");

        // convert/delete need a logged-in (org-member) session — oauth2Login() stands in for that.
        // Registration id must be "github" to match @RegisteredOAuth2AuthorizedClient("github").
        ClientRegistration githubRegistration = ClientRegistration.withRegistrationId("github")
            .clientId("test-client")
            .clientSecret("test-secret")
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .authorizationUri("https://github.com/login/oauth/authorize")
            .tokenUri("https://github.com/login/oauth/access_token")
            .userInfoUri("https://api.github.com/user")
            .userNameAttributeName("id")
            .clientName("GitHub")
            .build();
        var githubLogin = oauth2Login().clientRegistration(githubRegistration);

        mockMvc.perform(post("/v1/tracker/issues/" + id + "/convert").with(githubLogin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"githubRepo": "dtfb/sportshub-backend"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.githubIssueUrl").value("https://github.com/dtfb/sportshub-backend/issues/1"));

        mockMvc.perform(delete("/v1/tracker/issues/" + id).with(githubLogin))
            .andExpect(status().isNoContent());
    }
}
