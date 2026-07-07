package de.dtfb.sportshub.backend.league;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The league structure read (GET /v1/leagues/{id}/structure): the Tier -> Group subtree with a
 * participation count per group. Placed participations count; a null-group one does not.
 */
class LeagueStructureControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String leagueId;
    private String tierId;
    private String groupAId;
    private String groupBId;

    @PostConstruct
    void setup() throws Exception {
        String seasonId = id(createSeason());
        leagueId = id(createLeague(seasonId));
        tierId = id(createTier(leagueId));
        groupAId = id(createGroup(tierId, "Gruppe A"));
        groupBId = id(createGroup(tierId, "Gruppe B"));
        // Two placed in group A (count 2), none in group B (count 0), one unplaced (not counted anywhere).
        createParticipation(id(createTeam("Placed 1")), leagueId, groupAId);
        createParticipation(id(createTeam("Placed 2")), leagueId, groupAId);
        createParticipation(id(createTeam("Unplaced")), leagueId, null);
    }

    @Test
    void structure_listsTierGroupsAndParticipationCounts() throws Exception {
        String json = mockMvc.perform(get("/v1/leagues/" + leagueId + "/structure"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.leagueId").value(leagueId))
            .andExpect(jsonPath("$.tiers.length()").value(1))
            .andExpect(jsonPath("$.tiers[0].id").value(tierId))
            .andExpect(jsonPath("$.tiers[0].groups.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        // Groups have no guaranteed order, so select by id. Group A: 2 placed; group B: 0.
        // The unplaced participation is not counted anywhere.
        List<Integer> groupACount = JsonPath.read(json, "$..groups[?(@.id == '" + groupAId + "')].participationCount");
        List<Integer> groupBCount = JsonPath.read(json, "$..groups[?(@.id == '" + groupBId + "')].participationCount");
        assertThat(groupACount).containsExactly(2);
        assertThat(groupBCount).containsExactly(0);
    }

    @Test
    void structure_unknownLeague_isNotFound() throws Exception {
        mockMvc.perform(get("/v1/leagues/does-not-exist/structure"))
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

    private MvcResult createGroup(String tierId, String name) throws Exception {
        return mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s", "tierId": "%s", "groupState": "READY"}
                    """, name, tierId)))
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

    private void createParticipation(String teamId, String leagueId, String groupId) throws Exception {
        String group = groupId == null ? "null" : "\"" + groupId + "\"";
        mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": %s}
                    """, teamId, leagueId, group)))
            .andExpect(status().isCreated());
    }
    //endregion
}
