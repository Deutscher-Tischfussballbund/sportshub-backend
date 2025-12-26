package de.dtfb.sportshub.backend.event;

import com.jayway.jsonpath.JsonPath;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired
    MockMvc mockMvc;

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
        MvcResult season = createSeason();
        String uuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
        String location = createEventAndReturnLocation(uuid);
        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Turnier"));
    }

    @Test
    void updateEvent() throws Exception {
        MvcResult season = createSeason();
        String uuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
        String location = createEventAndReturnLocation(uuid);
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
        MvcResult result = mockMvc.perform(get("/api/v1/events")).andReturn();
        String json = result.getResponse().getContentAsString();

        List<String> uuids = JsonPath.read(json, "$[*].uuid");
        for (String uuid : uuids) {
            mockMvc.perform(delete("/api/v1/events/{uuid}", uuid))
                .andExpect(status().isOk());
        }

        mockMvc.perform(get("/api/v1/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deleteEvent_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/events/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    private MvcResult createSeason() throws Exception {
        return mockMvc.perform(post("/api/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "2025"}
                """)).andReturn();
    }

    private @NonNull String createEventAndReturnLocation(String seasonUuid) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonUuid": "%s"}
                    """, seasonUuid)))
            .andExpect(status().isCreated())
            .andReturn();

        String location = result.getResponse().getHeader("Location");
        assert location != null;
        return location;
    }
}
