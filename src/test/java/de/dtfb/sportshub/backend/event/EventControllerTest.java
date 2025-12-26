package de.dtfb.sportshub.backend.event;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

    String uuid;
    String location;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        uuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult event = createEvent(uuid);
        location = event.getResponse().getHeader("Location");
        assert location != null;
    }

    @Test
    void getAllEvents() throws Exception {
        mockMvc.perform(get("/api/v1/events")).andExpect(status().isOk());
    }

    @Test
    void getEvent_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/events/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void createAndGetEvent() throws Exception {
        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Turnier"));
    }

    @Test
    void updateEvent() throws Exception {
        mockMvc.perform(put(location)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Replacement",
                            "seasonUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Replacement"));
    }

    @Test
    void updateEvent_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/events/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Replacement"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteEvent() throws Exception {
        mockMvc.perform(delete(location)).andExpect(status().isOk());

        mockMvc.perform(get(location))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteEvent_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/events/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private MvcResult createSeason() throws Exception {
        return mockMvc.perform(post("/api/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "2025"}
                """)).andReturn();
    }

    private MvcResult createEvent(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
