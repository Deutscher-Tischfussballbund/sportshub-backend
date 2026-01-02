package de.dtfb.sportshub.backend.round;

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
class RoundControllerTest {

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
        String stageUuid = JsonPath.read(stage.getResponse().getContentAsString(), "$.uuid");
        MvcResult pool = createPool(stageUuid);
        uuid = JsonPath.read(pool.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult round = createRound(uuid);
        url = round.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllRounds() throws Exception {
        mockMvc.perform(get("/api/v1/rounds"))
            .andExpect(status().isOk());
    }

    @Test
    void getRound_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/rounds/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetRound() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Runde1"))
            .andExpect(jsonPath("$.index").value(1));
    }

    @Test
    void updateRound() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Round1",
                            "poolUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Round1"))
            .andExpect(jsonPath("$.index").value(1));
    }

    @Test
    void updateRound_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/rounds/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Round1"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRound() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRound_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/rounds/" + UUID.randomUUID()))
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

    private MvcResult createPool(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/pools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Pool1",
                            "tournamentMode": "SWISS",
                            "stageUuid": "%s",
                            "poolState": "READY"
                            }
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createRound(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde1",
                            "index": 1,
                            "poolUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
