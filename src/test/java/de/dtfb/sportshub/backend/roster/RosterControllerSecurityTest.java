package de.dtfb.sportshub.backend.roster;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roster authorization (L2): edit/submit gated by {@code @authz.canEditRoster} (team_admin or admin),
 * confirm/reopen by {@code @authz.canConfirmRoster} (admin above the team — not the submitter). Reads
 * open. {@code @authz} is mocked to control verdicts; a gate-pass then 404s on the unknown id, which
 * confirms authorization let the request through.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RosterControllerSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    private static final String ROSTER = "/v1/team-participations/p-x/roster";
    private static final String BODY = "{\"playerId\": \"player-x\"}";

    @Test
    void add_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post(ROSTER).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void add_whenNotEditor_isForbidden() throws Exception {
        Mockito.when(authz.canEditRoster(any())).thenReturn(false);
        mockMvc.perform(post(ROSTER).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isForbidden());
    }

    @Test
    void add_whenEditor_passesGate() throws Exception {
        Mockito.when(authz.canEditRoster(any())).thenReturn(true);
        mockMvc.perform(post(ROSTER).with(jwt()).contentType(MediaType.APPLICATION_JSON).content(BODY))
            .andExpect(status().isNotFound()); // participation p-x doesn't exist → gate passed
    }

    @Test
    void submit_whenNotEditor_isForbidden() throws Exception {
        Mockito.when(authz.canEditRoster(any())).thenReturn(false);
        mockMvc.perform(post(ROSTER + "/submit").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void confirm_whenNotAdmin_isForbidden() throws Exception {
        // A team editor is NOT enough to confirm — confirm needs canConfirmRoster (admin above the team).
        Mockito.when(authz.canEditRoster(any())).thenReturn(true);
        Mockito.when(authz.canConfirmRoster(any())).thenReturn(false);
        mockMvc.perform(post(ROSTER + "/confirm").with(jwt()))
            .andExpect(status().isForbidden());
    }

    @Test
    void confirm_whenAdmin_passesGate() throws Exception {
        Mockito.when(authz.canConfirmRoster(any())).thenReturn(true);
        mockMvc.perform(post(ROSTER + "/confirm").with(jwt()))
            .andExpect(status().isNotFound()); // gate passed → 404 on unknown participation
    }

    @Test
    void read_asAnyAuthenticatedUser_isAllowed() throws Exception {
        // unknown participation → 404 (not 401/403): reads require only authentication
        mockMvc.perform(get(ROSTER).with(jwt()))
            .andExpect(status().isNotFound());
    }
}
