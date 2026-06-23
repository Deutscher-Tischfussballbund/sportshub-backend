package de.dtfb.sportshub.backend.access.role;

import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleCatalogControllerTest extends AuthorizedControllerTest {

    @Test
    void roles_listsEveryRoleWithItsScopeType() throws Exception {
        mockMvc.perform(get("/v1/auth/roles"))
            .andExpect(status().isOk())
            // Every active role is present (wire values).
            .andExpect(jsonPath("$[*].role").value(hasItems(
                "admin", "region_admin", "club_admin", "team_admin", "event_organizer")))
            // Scope type travels with the role.
            .andExpect(jsonPath("$[?(@.role=='region_admin')].scopeType").value(contains("region")))
            .andExpect(jsonPath("$[?(@.role=='event_organizer')].scopeType").value(contains("event")));
    }

    @Test
    void roles_flagDeprecatedEntries() throws Exception {
        mockMvc.perform(get("/v1/auth/roles"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.role=='region_admin')].deprecated").value(contains(false)))
            .andExpect(jsonPath("$[?(@.role=='tournament_uploader')].deprecated").value(contains(true)))
            .andExpect(jsonPath("$[?(@.role=='region_tournament_uploader')].deprecated").value(contains(true)));
    }
}
