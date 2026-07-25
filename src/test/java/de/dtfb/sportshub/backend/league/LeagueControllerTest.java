package de.dtfb.sportshub.backend.league;

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


class LeagueControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String seasonId;
    private String categoryId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        seasonId = JsonPath.read(createSeason().getResponse().getContentAsString(), "$.id");
        categoryId = createCategory();
    }

    @BeforeEach
    void setupEach() throws Exception {
        url = createLeague(seasonId, categoryId).getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllLeagues() throws Exception {
        mockMvc.perform(get("/v1/leagues")).andExpect(status().isOk());
    }

    @Test
    void getLeague_expectException() throws Exception {
        mockMvc.perform(get("/v1/leagues/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetLeague() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Bayernliga"))
            .andExpect(jsonPath("$.seasonId").value(seasonId))
            .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    @Test
    void updateLeague() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Landesliga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, categoryId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Landesliga"));
    }

    @Test
    void updateLeague_expectException() throws Exception {
        mockMvc.perform(put("/v1/leagues/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Landesliga"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteLeague() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteLeague_expectException() throws Exception {
        mockMvc.perform(delete("/v1/leagues/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteLeague_blockedByTier() throws Exception {
        String leagueId = idFromUrl(url);
        createTier(leagueId);

        mockMvc.perform(delete(url))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LEAGUE_HAS_STRUCTURE_OR_PARTICIPATIONS"));

        // untouched -- still there, still deletable once the block is lifted (not exercised here)
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void deleteLeague_blockedByParticipation() throws Exception {
        String leagueId = idFromUrl(url);
        String teamId = id(createTeam("Testteam"));
        createParticipation(teamId, leagueId);

        mockMvc.perform(delete(url))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LEAGUE_HAS_STRUCTURE_OR_PARTICIPATIONS"));
    }

    //region helpers
    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String idFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MvcResult createSeason() throws Exception {
        String federationId = createFederation();
        return mockMvc.perform(post("/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                {"name": "2025", "federationId": "%s"}
                """, federationId))).andReturn();
    }

    private MvcResult createLeague(String seasonId, String categoryId) throws Exception {
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

    private MvcResult createTeam(String name) throws Exception {
        String clubId = createClub();
        return mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s", "clubId": "%s"}
                    """, name, clubId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createParticipation(String teamId, String leagueId) throws Exception {
        return mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, leagueId)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
