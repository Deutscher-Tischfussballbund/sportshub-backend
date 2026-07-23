package de.dtfb.sportshub.backend.season;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class SeasonControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String url;

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult season = createSeason();
        url = season.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllSeasons() throws Exception {
        mockMvc.perform(get("/v1/seasons"))
            .andExpect(status().isOk());
    }

    @Test
    void getSeason_expectException() throws Exception {
        mockMvc.perform(get("/v1/seasons/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetSeason() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("2000"));
    }

    @Test
    void updateSeason() throws Exception {
        String federationId = createFederation();
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2024", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("2024"));
    }

    @Test
    void registrationOpen_isFalse_whenNoWindowSet() throws Exception {
        // created in setupEach() with no registration window at all
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationOpen").value(false));
    }

    @Test
    void registrationOpen_isTrue_withinWindow() throws Exception {
        setWindow(java.time.LocalDate.now().minusDays(10), java.time.LocalDate.now().plusDays(10));
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationOpen").value(true));
    }

    @Test
    void registrationOpen_isFalse_beforeOpensAt() throws Exception {
        setWindow(java.time.LocalDate.now().plusDays(1), java.time.LocalDate.now().plusDays(10));
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationOpen").value(false));
    }

    @Test
    void registrationOpen_isFalse_afterClosesAt() throws Exception {
        setWindow(java.time.LocalDate.now().minusDays(10), java.time.LocalDate.now().minusDays(1));
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationOpen").value(false));
    }

    @Test
    void registrationOpen_staysTrue_whenClosesAtOmitted() throws Exception {
        // opened long ago, no close date -> stays open indefinitely
        String federationId = createFederation();
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2000", "federationId": "%s", "registrationOpensAt": "%s"}
                    """, federationId, java.time.LocalDate.now().minusYears(1))))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationOpen").value(true))
            .andExpect(jsonPath("$.registrationClosesAt").isEmpty());
    }

    @Test
    void updateSeason_expectException() throws Exception {
        mockMvc.perform(put("/v1/seasons/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "2024"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteSeason() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteSeason_expectException() throws Exception {
        mockMvc.perform(delete("/v1/seasons/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void archive_hidesFromActiveList_showsInArchived() throws Exception {
        mockMvc.perform(post(url + "/archive"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").isNotEmpty());

        mockMvc.perform(get("/v1/seasons"))
            .andExpect(jsonPath("$[?(@.id=='" + seasonId() + "')]").value(empty()));
        mockMvc.perform(get("/v1/seasons/archived"))
            .andExpect(jsonPath("$[?(@.id=='" + seasonId() + "')].name").value(hasItem("2000")));
    }

    @Test
    void unarchive_restoresToActiveList() throws Exception {
        mockMvc.perform(post(url + "/archive")).andExpect(status().isOk());

        mockMvc.perform(post(url + "/unarchive"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedAt").isEmpty());

        mockMvc.perform(get("/v1/seasons"))
            .andExpect(jsonPath("$[?(@.id=='" + seasonId() + "')].name").value(hasItem("2000")));
    }

    @Test
    void deleteSeason_withResultFreeStructure_cascades() throws Exception {
        String leagueId = createLeague(seasonId());

        // No results yet → delete is allowed and wipes the structure (exercises SeasonStructure JPQL).
        mockMvc.perform(delete(url)).andExpect(status().isNoContent());

        mockMvc.perform(get(url)).andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/leagues/" + leagueId)).andExpect(status().isNotFound());
    }

    @Test
    void archivedSeason_hidesItsLeagues() throws Exception {
        String leagueId = createLeague(seasonId());

        mockMvc.perform(post(url + "/archive")).andExpect(status().isOk());

        mockMvc.perform(get("/v1/leagues"))
            .andExpect(jsonPath("$[?(@.id=='" + leagueId + "')]").value(empty()));
        mockMvc.perform(get("/v1/leagues/" + leagueId)).andExpect(status().isNotFound());
    }

    @Test
    void archivedSeason_hidesDeeperEntities() throws Exception {
        // A tier lives below league → season; archiving the season must hide it too.
        String leagueId = createLeague(seasonId());
        String tierId = createTier(leagueId);

        mockMvc.perform(post(url + "/archive")).andExpect(status().isOk());

        mockMvc.perform(get("/v1/tiers"))
            .andExpect(jsonPath("$[?(@.id=='" + tierId + "')]").value(empty()));
        mockMvc.perform(get("/v1/tiers/" + tierId)).andExpect(status().isNotFound());
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
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2000", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    /** Id of the season created in {@link #setupEach()} (last path segment of its Location). */
    private String seasonId() {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    /** Set the registration window on the season created in {@link #setupEach()}. */
    private void setWindow(java.time.LocalDate opensAt, java.time.LocalDate closesAt) throws Exception {
        String federationId = createFederation();
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2000", "federationId": "%s", "registrationOpensAt": "%s", "registrationClosesAt": "%s"}
                    """, federationId, opensAt, closesAt)))
            .andExpect(status().isOk());
    }

    private String createTier(String leagueId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Liga", "leagueId": "%s"}
                    """, leagueId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createLeague(String seasonId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Liga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, createCategory())))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    //endregion
}
