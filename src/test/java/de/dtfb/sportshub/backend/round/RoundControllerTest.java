package de.dtfb.sportshub.backend.round;

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


class RoundControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String groupId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        String seasonId = id(createSeason());
        String leagueId = id(createLeague(seasonId));
        String tierId = id(createTier(leagueId));
        groupId = id(createGroup(tierId));
    }

    @BeforeEach
    void setupEach() throws Exception {
        url = createRound(groupId).getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllRounds() throws Exception {
        mockMvc.perform(get("/v1/rounds")).andExpect(status().isOk());
    }

    @Test
    void getRound_expectException() throws Exception {
        mockMvc.perform(get("/v1/rounds/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetRound() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Runde1"))
            .andExpect(jsonPath("$.groupId").value(groupId));
    }

    @Test
    void updateRound() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde2", "index": 2, "groupId": "%s"}
                    """, groupId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Runde2"));
    }

    @Test
    void updateRound_expectException() throws Exception {
        mockMvc.perform(put("/v1/rounds/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Runde2"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteRound() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteRound_expectException() throws Exception {
        mockMvc.perform(delete("/v1/rounds/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    //region helpers
    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private MvcResult createSeason() throws Exception {
        String federationId = createFederation();
        return mockMvc.perform(post("/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                {"name": "2025", "federationId": "%s"}
                """, federationId))).andReturn();
    }

    private MvcResult createLeague(String seasonId) throws Exception {
        String categoryId = createCategory();
        return mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, categoryId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createTier(String leagueId) throws Exception {
        return mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bayernliga", "leagueId": "%s"}
                    """, leagueId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createGroup(String tierId) throws Exception {
        return mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Gruppe A", "tierId": "%s", "groupState": "READY"}
                    """, tierId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createRound(String groupId) throws Exception {
        return mockMvc.perform(post("/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde1", "index": 1, "groupId": "%s"}
                    """, groupId)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
