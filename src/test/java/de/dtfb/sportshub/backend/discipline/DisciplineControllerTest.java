package de.dtfb.sportshub.backend.discipline;

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
class DisciplineControllerTest {

    @Autowired
    MockMvc mockMvc;

    String uuid;
    String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonUuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
        MvcResult event = createEvent(seasonUuid);
        uuid = JsonPath.read(event.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult discipline = createDiscipline(uuid);
        url = discipline.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllDisciplines() throws Exception {
        mockMvc.perform(get("/api/v1/disciplines"))
            .andExpect(status().isOk());
    }

    @Test
    void getDiscipline_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/disciplines/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetDiscipline() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Offenes Einzel"));
    }

    @Test
    void updateDiscipline() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Replacement",
                            "eventUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Replacement"));
    }

    @Test
    void updateDiscipline_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/disciplines/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Replacement"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteDiscipline() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteDiscipline_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/disciplines/" + UUID.randomUUID()))
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
                    """))
            .andReturn();
    }

    private MvcResult createEvent(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonUuid": "%s"}
                    """, uuid)))
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
    //endregion
}
