package de.dtfb.sportshub.backend.teamparticipation;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CopyForwardControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String federationId;
    private String sourceSeasonId;
    private String sourceGroupId;
    private String teamId;
    private String sourceParticipationId;
    private String targetSeasonId;

    @BeforeEach
    void setup() throws Exception {
        // A source season with a full league subtree and one placed team.
        federationId = createFederation();
        sourceSeasonId = createSeason(federationId);
        String leagueId = createLeague(sourceSeasonId);
        String tierId = createTier(leagueId);
        sourceGroupId = createGroup(tierId);
        teamId = createTeam();
        sourceParticipationId = createParticipation(leagueId, sourceGroupId);

        // An empty target season in the SAME federation.
        targetSeasonId = createSeason(federationId);
    }

    @Test
    void copyForward_clonesStructureAndPlacements() throws Exception {
        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward").param("from", sourceSeasonId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.leagues").value(1))
            .andExpect(jsonPath("$.tiers").value(1))
            .andExpect(jsonPath("$.groups").value(1))
            .andExpect(jsonPath("$.participations").value(1));

        String json = mockMvc.perform(get("/v1/team-participations").param("seasonId", targetSeasonId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].teamId").value(teamId))
            .andExpect(jsonPath("$[0].seasonId").value(targetSeasonId))
            // the audit chain points back to the source placement
            .andExpect(jsonPath("$[0].copiedFromParticipationId").value(sourceParticipationId))
            .andReturn().getResponse().getContentAsString();

        // the placement is in a freshly cloned group, not the source group
        String clonedGroupId = JsonPath.read(json, "$[0].groupId");
        assert clonedGroupId != null && !clonedGroupId.equals(sourceGroupId);
    }

    @Test
    void copyForward_clonesActiveRosterByDefault() throws Exception {
        addToRoster(sourceParticipationId, "player-test");
        addToRoster(sourceParticipationId, "player-club");
        // a removed player must NOT come along
        String removedPlayerId = "player-p1";
        addToRoster(sourceParticipationId, removedPlayerId);
        mockMvc.perform(delete(rosterUrl(sourceParticipationId) + "/" + removedPlayerId))
            .andExpect(status().isOk());

        String json = mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward")
                .param("from", sourceSeasonId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterEntries").value(2))
            .andReturn().getResponse().getContentAsString();

        String clonedParticipationId = JsonPath.read(
            mockMvc.perform(get("/v1/team-participations").param("seasonId", targetSeasonId))
                .andReturn().getResponse().getContentAsString(),
            "$[0].id");

        mockMvc.perform(get(rosterUrl(clonedParticipationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].playerId").value(org.hamcrest.Matchers.containsInAnyOrder(
                "player-test", "player-club")));

        // cloned participation stays editable — untouched by the copy
        mockMvc.perform(get("/v1/team-participations/" + clonedParticipationId))
            .andExpect(jsonPath("$.rosterStatus").value("DRAFT"));
    }

    @Test
    void copyForward_skipsRosterWhenOptedOut() throws Exception {
        addToRoster(sourceParticipationId, "player-test");

        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward")
                .param("from", sourceSeasonId)
                .param("copyRoster", "false"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterEntries").value(0));

        String clonedParticipationId = JsonPath.read(
            mockMvc.perform(get("/v1/team-participations").param("seasonId", targetSeasonId))
                .andReturn().getResponse().getContentAsString(),
            "$[0].id");

        mockMvc.perform(get(rosterUrl(clonedParticipationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void copyForward_rejectsWhenTargetNotEmpty() throws Exception {
        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward").param("from", sourceSeasonId))
            .andExpect(status().isOk());

        // second run would duplicate placements — refused
        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward").param("from", sourceSeasonId))
            .andExpect(status().isConflict());
    }

    @Test
    void copyForward_rejectsDifferentFederation() throws Exception {
        String otherSeasonId = createSeason(createFederation());
        mockMvc.perform(post("/v1/seasons/" + otherSeasonId + "/copy-forward").param("from", sourceSeasonId))
            .andExpect(status().isBadRequest());
    }

    @Test
    void copyForward_unknownSource_isNotFound() throws Exception {
        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward")
                .param("from", NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    //region helpers
    private String createSeason(String federationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "Season", "federationId": "%s", "registrationOpen": true}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createLeague(String seasonId) throws Exception {
        String categoryId = createCategory();
        MvcResult result = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTier(String leagueId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bayernliga", "leagueId": "%s"}
                    """, leagueId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createGroup(String tierId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bundesliga", "tierId": "%s", "groupState": "FINISHED"}
                    """, tierId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTeam() throws Exception {
        String clubId = createClub();
        MvcResult result = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "TFC München 1", "clubId": "%s"}
                    """, clubId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createParticipation(String leagueId, String groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": "%s"}
                    """, teamId, leagueId, groupId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String rosterUrl(String participationId) {
        return "/v1/team-participations/" + participationId + "/roster";
    }

    private void addToRoster(String participationId, String playerId) throws Exception {
        mockMvc.perform(post(rosterUrl(participationId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"playerId\": \"%s\"}", playerId)))
            .andExpect(status().isCreated());
    }
    //endregion
}
