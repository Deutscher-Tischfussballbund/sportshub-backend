package de.dtfb.sportshub.backend.stage;

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
class StageControllerTest {

    @Autowired
    MockMvc mockMvc;

    String uuid;
    String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonUuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
        MvcResult event = createEvent(seasonUuid);
        String eventUuid = JsonPath.read(event.getResponse().getContentAsString(), "$.uuid");
        MvcResult discipline = createDiscipline(eventUuid);
        uuid = JsonPath.read(discipline.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult event = createStage(uuid);
        url = event.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllStages() throws Exception {
        mockMvc.perform(get("/api/v1/stages"))
            .andExpect(status().isOk());
    }

    @Test
    void getStage_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/stages/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetStage() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Vorrunde"));
    }

    @Test
    void updateStage() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Hauptrunde",
                            "disciplineUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hauptrunde"));
    }

    @Test
    void updateStage_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/stages/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Hauptrunde"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteStage() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteStage_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/stages/" + UUID.randomUUID()))
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

    private MvcResult createDiscipline(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Offenes Einzel",
                            "eventUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createStage(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Vorrunde",
                            "disciplineUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
