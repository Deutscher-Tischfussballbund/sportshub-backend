package de.dtfb.sportshub.backend.maintenance;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Maintenance windows (SPO-119): shown from the announcement until the window ends. */
class MaintenanceNoticeControllerTest extends AuthorizedControllerTest {

    @Autowired
    private MaintenanceNoticeRepository repository;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void current_isShownFromTheAnnouncementUntilTheEnd() throws Exception {
        Instant now = Instant.now();
        create(now.plus(Duration.ofDays(3)), now.plus(Duration.ofDays(3)).plus(Duration.ofHours(2)), now.plus(Duration.ofDays(1)))
            .andExpect(status().isCreated());
        mockMvc.perform(get("/v1/maintenance-notices/current")).andExpect(status().isNoContent());

        String running = id(create(now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)), now.minus(Duration.ofDays(1))));
        mockMvc.perform(get("/v1/maintenance-notices/current"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(running))
            .andExpect(jsonPath("$.messageDe").value("Wartung"));

        create(now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(1)), now.minus(Duration.ofDays(2)));
        mockMvc.perform(get("/v1/admin/maintenance-notices")).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void announceFrom_defaultsToTheStart_andMayNotBeLater() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        create(start, start.plus(Duration.ofHours(1)), null)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.announceFrom").value(org.hamcrest.Matchers.startsWith(start.toString().substring(0, 16))));
        create(start, start.plus(Duration.ofHours(1)), start.plus(Duration.ofMinutes(5))).andExpect(status().isBadRequest());
        create(start, start.minus(Duration.ofHours(1)), null).andExpect(status().isBadRequest());
    }

    @Test
    void edit_andDelete() throws Exception {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        String id = id(create(start, start.plus(Duration.ofHours(1)), null));
        mockMvc.perform(put("/v1/admin/maintenance-notices/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(start, start.plus(Duration.ofHours(2)), null).replace("Wartung", "Längere Wartung")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messageDe").value("Längere Wartung"));
        mockMvc.perform(delete("/v1/admin/maintenance-notices/" + id)).andExpect(status().isOk());
        mockMvc.perform(get("/v1/admin/maintenance-notices")).andExpect(jsonPath("$.length()").value(0));
    }

    private ResultActions create(Instant start, Instant end, Instant announceFrom) throws Exception {
        return mockMvc.perform(post("/v1/admin/maintenance-notices")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(start, end, announceFrom)));
    }

    private static String body(Instant start, Instant end, Instant announceFrom) {
        return """
            {"messageDe": "Wartung", "messageEn": "Maintenance", "startsAt": "%s", "endsAt": "%s", "announceFrom": %s}
            """.formatted(start, end, announceFrom == null ? "null" : "\"" + announceFrom + "\"");
    }

    private static String id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
