package de.dtfb.sportshub.backend.teamparticipation;

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

class TeamParticipationControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String seasonId;
    private String leagueId;
    private String groupId;
    private String teamId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        seasonId = id(createSeason());
        leagueId = id(createLeague(seasonId));
        String tierId = id(createTier(leagueId));
        groupId = id(createGroup(tierId));
        teamId = id(createTeam("Hand und Foos"));
    }

    @BeforeEach
    void setupEach() throws Exception {
        // Register a team without a division yet (registered-but-unplaced).
        url = createParticipation(null).getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllParticipations() throws Exception {
        mockMvc.perform(get("/v1/team-participations")).andExpect(status().isOk());
    }

    @Test
    void getParticipation_expectException() throws Exception {
        mockMvc.perform(get("/v1/team-participations/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetParticipation() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.teamId").value(teamId))
            .andExpect(jsonPath("$.leagueId").value(leagueId))
            // season is derived from the league, not sent by the client
            .andExpect(jsonPath("$.seasonId").value(seasonId));
    }

    @Test
    void updateParticipation_placesIntoGroup() throws Exception {
        // promote/relegate: move the team into a division
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": "%s"}
                    """, teamId, leagueId, groupId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").value(groupId));
    }

    @Test
    void updateParticipation_expectException() throws Exception {
        mockMvc.perform(put("/v1/team-participations/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, leagueId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation_expectException() throws Exception {
        mockMvc.perform(delete("/v1/team-participations/" + NanoIdUtils.randomNanoId()))
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
                            {"name": "1. Bundesliga", "tierId": "%s", "groupState": "PLANNED"}
                    """, tierId)))
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

    private MvcResult createParticipation(String groupId) throws Exception {
        String group = groupId == null ? "null" : "\"" + groupId + "\"";
        return mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": %s}
                    """, teamId, leagueId, group)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
