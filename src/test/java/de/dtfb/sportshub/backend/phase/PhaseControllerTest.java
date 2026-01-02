package de.dtfb.sportshub.backend.phase;

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
class PhaseControllerTest {

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
        String disciplineUuid = JsonPath.read(discipline.getResponse().getContentAsString(), "$.uuid");
        MvcResult stage = createStage(disciplineUuid);
        uuid = JsonPath.read(stage.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult phase = createPhase(uuid);
        url = phase.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllPhases() throws Exception {
        mockMvc.perform(get("/api/v1/phases"))
            .andExpect(status().isOk());
    }

    @Test
    void getPhase_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/phases/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetPhase() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Phase1"))
            .andExpect(jsonPath("$.tournamentMode").value("SWISS"))
            .andExpect(jsonPath("$.phaseState").value("PLACEHOLDER"));
    }

    @Test
    void updatePhase() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Phase1",
                            "tournamentMode": "LORD_HAVE_MERCY",
                            "phaseState": "PLACEHOLDER",
                            "stageUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Phase1"))
            .andExpect(jsonPath("$.tournamentMode").value("LORD_HAVE_MERCY"))
            .andExpect(jsonPath("$.phaseState").value("PLACEHOLDER"));
    }

    @Test
    void updatePhase_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/phases/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Phase1",
                            "tournamentMode": "LORD_HAVE_MERCY",
                            "phaseState": "PLACEHOLDER"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletePhase() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deletePhase_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/phases/" + UUID.randomUUID()))
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

    private MvcResult createPhase(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/phases")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Phase1",
                            "tournamentMode": "SWISS",
                            "stageUuid": "%s",
                            "phaseState": "PLACEHOLDER"
                            }
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
