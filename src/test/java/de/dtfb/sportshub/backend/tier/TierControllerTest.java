package de.dtfb.sportshub.backend.tier;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TierControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String competitionId;
    String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");
        MvcResult competition = createCompetition(seasonId);
        competitionId = JsonPath.read(competition.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult tier = createTier(competitionId);
        url = tier.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllTiers() throws Exception {
        mockMvc.perform(get("/v1/tiers"))
            .andExpect(status().isOk());
    }

    @Test
    void getTier_expectException() throws Exception {
        mockMvc.perform(get("/v1/tiers/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetTier() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("1. Bayernliga"))
            .andExpect(jsonPath("$.competitionId").value(competitionId));
    }

    @Test
    void updateTier() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2. Bayernliga",
                            "competitionId": "%s"}
                    """, competitionId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("2. Bayernliga"));
    }

    @Test
    void updateTier_expectException() throws Exception {
        mockMvc.perform(put("/v1/tiers/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "2. Bayernliga"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTier() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTier_expectException() throws Exception {
        mockMvc.perform(delete("/v1/tiers/" + NanoIdUtils.randomNanoId()))
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

    private MvcResult createCompetition(String seasonId) throws Exception {
        return mockMvc.perform(post("/v1/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Bayernliga",
                            "seasonId": "%s"}
                    """, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createTier(String competitionId) throws Exception {
        return mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bayernliga",
                            "competitionId": "%s"}
                    """, competitionId)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
