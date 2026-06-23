package de.dtfb.sportshub.backend.discipline;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class DisciplineControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String uuid;
    String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");
        MvcResult event = createEvent(seasonId);
        uuid = JsonPath.read(event.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult discipline = createDiscipline(uuid);
        url = discipline.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllDisciplines() throws Exception {
        mockMvc.perform(get("/v1/disciplines"))
            .andExpect(status().isOk());
    }

    @Test
    void getDiscipline_expectException() throws Exception {
        mockMvc.perform(get("/v1/disciplines/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetDiscipline() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eventId").value(uuid));
    }

    @Test
    void updateDiscipline() throws Exception {
        String categoryId = createCategory();
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Replacement",
                            "eventId": "%s",
                            "categoryId": "%s"}
                    """, uuid, categoryId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    @Test
    void updateDiscipline_expectException() throws Exception {
        mockMvc.perform(put("/v1/disciplines/" + NanoIdUtils.randomNanoId())
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
        mockMvc.perform(delete("/v1/disciplines/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private MvcResult createSeason() throws Exception {
        String federationId = createFederation();
        return mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                            {"name": "2025", "federationId": "%s"}
                    """, federationId)))
            .andReturn();
    }

    private MvcResult createEvent(String uuid) throws Exception {
        return mockMvc.perform(post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonId": "%s"}
                    """, uuid)))
            .andReturn();
    }

    private MvcResult createDiscipline(String uuid) throws Exception {
        String categoryId = createCategory();
        return mockMvc.perform(post("/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Offenes Einzel",
                            "eventId": "%s",
                            "categoryId": "%s"}
                    """, uuid, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
