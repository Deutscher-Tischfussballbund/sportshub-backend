package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.access.apiclient.ApiClientAuthorizationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code PUT /v1/matches/{id}} is reachable via either authorization path (see MatchController's
 * {@code @PreAuthorize}) -- these cover the app/service-client path specifically
 * ({@code @apiClientAuthz}), mocked independently of the human path ({@code @authz}), mirroring
 * how {@code CategoryControllerSecurityTest} mocks {@code AuthorizationService} directly rather
 * than seeding real roles/grants.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MatchControllerApiClientSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    @MockitoBean
    ApiClientAuthorizationService apiClientAuthz;

    private static final String BODY = """
        {"state": "PLANNED"}
        """;

    @Test
    void update_apiClientGranted_humanDenied_isAllowed() throws Exception {
        Mockito.when(apiClientAuthz.canOrganizeMatch(Mockito.anyString())).thenReturn(true);
        Mockito.when(authz.canOrganizeMatch(Mockito.anyString())).thenReturn(false);

        mockMvc.perform(put("/v1/matches/some-id").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound()); // apiClientAuthz allows the @PreAuthorize gate through;
        // service.update() 404s on the fake id -- proves the gate passed, not the domain logic.
    }

    @Test
    void update_bothDenied_isForbidden() throws Exception {
        Mockito.when(apiClientAuthz.canOrganizeMatch(Mockito.anyString())).thenReturn(false);
        Mockito.when(authz.canOrganizeMatch(Mockito.anyString())).thenReturn(false);

        mockMvc.perform(put("/v1/matches/some-id").with(jwt())
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }
}
