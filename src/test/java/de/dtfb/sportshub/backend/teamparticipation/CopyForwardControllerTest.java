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
    private String sourcePoolId;
    private String teamId;
    private String sourceParticipationId;
    private String targetSeasonId;

    @BeforeEach
    void setup() throws Exception {
        // A source season with a full competition subtree and one placed team.
        federationId = createFederation();
        sourceSeasonId = createSeason(federationId);
        String competitionId = createCompetition(sourceSeasonId);
        String disciplineId = createDiscipline(competitionId);
        String stageId = createStage(disciplineId);
        sourcePoolId = createPool(stageId);
        teamId = createTeam();
        sourceParticipationId = createParticipation(competitionId, sourcePoolId);

        // An empty target season in the SAME federation.
        targetSeasonId = createSeason(federationId);
    }

    @Test
    void copyForward_clonesStructureAndPlacements() throws Exception {
        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward").param("from", sourceSeasonId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.competitions").value(1))
            .andExpect(jsonPath("$.disciplines").value(1))
            .andExpect(jsonPath("$.stages").value(1))
            .andExpect(jsonPath("$.pools").value(1))
            .andExpect(jsonPath("$.participations").value(1));

        String json = mockMvc.perform(get("/v1/team-participations").param("seasonId", targetSeasonId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].teamId").value(teamId))
            .andExpect(jsonPath("$[0].seasonId").value(targetSeasonId))
            // the audit chain points back to the source placement
            .andExpect(jsonPath("$[0].copiedFromParticipationId").value(sourceParticipationId))
            .andReturn().getResponse().getContentAsString();

        // the placement is in a freshly cloned pool, not the source pool
        String clonedPoolId = JsonPath.read(json, "$[0].poolId");
        assert clonedPoolId != null && !clonedPoolId.equals(sourcePoolId);
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

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private String createSeason(String federationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "Season", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createCompetition(String seasonId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Bayernliga", "seasonId": "%s"}
                    """, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createDiscipline(String competitionId) throws Exception {
        String categoryId = createCategory();
        MvcResult result = mockMvc.perform(post("/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Herren", "competitionId": "%s", "categoryId": "%s"}
                    """, competitionId, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createStage(String disciplineId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Hauptrunde", "disciplineId": "%s"}
                    """, disciplineId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createPool(String stageId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/pools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bundesliga", "tournamentMode": "round robin",
                            "stageId": "%s", "poolState": "FINISHED"}
                    """, stageId)))
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

    private String createParticipation(String competitionId, String poolId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "competitionId": "%s", "poolId": "%s"}
                    """, teamId, competitionId, poolId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    //endregion
}
