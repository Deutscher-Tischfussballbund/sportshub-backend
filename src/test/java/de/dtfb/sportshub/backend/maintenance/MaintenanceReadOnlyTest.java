package de.dtfb.sportshub.backend.maintenance;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import de.dtfb.sportshub.backend.support.TestIds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A running read-only maintenance window (SPO-119) refuses every change except a global admin's, with
 * 423 -- reads stay open. Categories stand in for every write endpoint: the filter sits in front of all.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MaintenanceReadOnlyTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MaintenanceNoticeRepository repository;

    @MockitoBean
    AuthorizationService authz;

    @AfterEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void runningReadOnlyWindow_refusesChanges_butNotReads() throws Exception {
        window(true, Instant.now().minus(Duration.ofMinutes(5)));
        Mockito.when(authz.isAdmin()).thenReturn(false);

        createCategory()
            .andExpect(status().is(423))
            .andExpect(jsonPath("$.code").value("MAINTENANCE_READ_ONLY"));
        mockMvc.perform(get("/v1/categories").with(jwt())).andExpect(status().isOk());
    }

    @Test
    void globalAdmin_mayStillChange() throws Exception {
        window(true, Instant.now().minus(Duration.ofMinutes(5)));
        Mockito.when(authz.isAdmin()).thenReturn(true);

        createCategory().andExpect(status().isCreated());
    }

    @Test
    void noReadOnlyWindowRunning_leavesTheNormalRules() throws Exception {
        window(false, Instant.now().minus(Duration.ofMinutes(5)));
        window(true, Instant.now().plus(Duration.ofHours(1)));
        Mockito.when(authz.isAdmin()).thenReturn(false);

        // Not refused by maintenance -- the category endpoint's own admin check answers.
        createCategory().andExpect(status().isForbidden());
    }

    private void window(boolean readOnly, Instant start) {
        MaintenanceNotice notice = new MaintenanceNotice();
        notice.setMessageDe("Wartung");
        notice.setMessageEn("Maintenance");
        notice.setStartsAt(start);
        notice.setEndsAt(start.plus(Duration.ofHours(2)));
        notice.setAnnounceFrom(start.minus(Duration.ofDays(1)));
        notice.setReadOnly(readOnly);
        notice.setUpdatedAt(Instant.now());
        repository.save(notice);
    }

    private ResultActions createCategory() throws Exception {
        return mockMvc.perform(post("/v1/categories").with(jwt())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\": \"C\", \"shortName\": \"" + TestIds.unique("RO") + "\"}"));
    }
}
