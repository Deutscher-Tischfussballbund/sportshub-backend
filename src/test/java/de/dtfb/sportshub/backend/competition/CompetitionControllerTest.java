package de.dtfb.sportshub.backend.competition;

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


class CompetitionControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String uuid;
    String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        uuid = JsonPath.read(season.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult competition = createCompetition(uuid);
        url = competition.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllCompetitions() throws Exception {
        mockMvc.perform(get("/v1/competitions"))
            .andExpect(status().isOk());
    }

    @Test
    void getCompetition_expectException() throws Exception {
        mockMvc.perform(get("/v1/competitions/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetCompetition() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Turnier"));
    }

    @Test
    void updateCompetition() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Replacement",
                            "seasonId": "%s"}
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Replacement"));
    }

    @Test
    void updateCompetition_expectException() throws Exception {
        mockMvc.perform(put("/v1/competitions/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Replacement"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteCompetition() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteCompetition_expectException() throws Exception {
        mockMvc.perform(delete("/v1/competitions/" + NanoIdUtils.randomNanoId()))
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
                """, federationId))).andReturn();
    }

    private MvcResult createCompetition(String uuid) throws Exception {
        return mockMvc.perform(post("/v1/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
