package de.dtfb.sportshub.backend.group;

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


class GroupControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String tierId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        String seasonId = id(createSeason());
        String leagueId = id(createLeague(seasonId));
        tierId = id(createTier(leagueId));
    }

    @BeforeEach
    void setupEach() throws Exception {
        url = createGroup(tierId).getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllGroups() throws Exception {
        mockMvc.perform(get("/v1/groups")).andExpect(status().isOk());
    }

    @Test
    void getGroup_expectException() throws Exception {
        mockMvc.perform(get("/v1/groups/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetGroup() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Gruppe A"))
            .andExpect(jsonPath("$.tierId").value(tierId))
            .andExpect(jsonPath("$.groupState").value("READY"));
    }

    @Test
    void updateGroup() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Gruppe B", "tierId": "%s", "groupState": "RUNNING"}
                    """, tierId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Gruppe B"))
            .andExpect(jsonPath("$.groupState").value("RUNNING"));
    }

    @Test
    void updateGroup_expectException() throws Exception {
        mockMvc.perform(put("/v1/groups/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Gruppe B", "groupState": "PLANNED"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteGroup_expectException() throws Exception {
        mockMvc.perform(delete("/v1/groups/" + NanoIdUtils.randomNanoId()))
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
    //endregion
}
