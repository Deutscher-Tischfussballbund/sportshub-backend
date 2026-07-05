package de.dtfb.sportshub.backend.competition;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The competition-structure read: {@code GET /v1/competitions/{id}/structure} returns the
 * Discipline→Stage→Pool subtree with a participation count per pool. Placed participations are
 * counted; an unplaced one (null pool) is not.
 */
class CompetitionStructureControllerTest extends AuthorizedControllerTest {

    private String competitionId;
    private String categoryId;
    private String poolAId;
    private String poolBId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = createSeason(federationId);
        competitionId = createCompetition(seasonId);
        String disciplineId = createDiscipline(competitionId);
        categoryId = JsonPath.read(
            mockMvc.perform(get("/v1/disciplines/" + disciplineId)).andReturn().getResponse().getContentAsString(),
            "$.categoryId");
        String stageId = createStage(disciplineId);
        poolAId = createPool(stageId, "1. Bayernliga – Gruppe A");
        poolBId = createPool(stageId, "1. Bayernliga – Gruppe B");

        // Two teams placed in pool A, none in pool B, plus one unplaced participation (null pool).
        createParticipation(competitionId, poolAId);
        createParticipation(competitionId, poolAId);
        createParticipation(competitionId, null);
    }

    @Test
    void structure_returnsTreeWithParticipationCounts() throws Exception {
        String json = mockMvc.perform(get("/v1/competitions/" + competitionId + "/structure"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.competitionId").value(competitionId))
            .andExpect(jsonPath("$.name").value("Bayernliga"))
            .andExpect(jsonPath("$.disciplines.length()").value(1))
            .andExpect(jsonPath("$.disciplines[0].categoryId").value(categoryId))
            .andExpect(jsonPath("$.disciplines[0].stages.length()").value(1))
            .andExpect(jsonPath("$.disciplines[0].stages[0].name").value("Hauptrunde"))
            .andExpect(jsonPath("$.disciplines[0].stages[0].pools.length()").value(2))
            .andReturn().getResponse().getContentAsString();

        // pool A: two placed teams; pool B: none. The unplaced participation is not counted anywhere.
        // Pools have no guaranteed order, so select by id rather than index.
        List<Integer> poolACount = JsonPath.read(json, "$..pools[?(@.id == '" + poolAId + "')].participationCount");
        List<Integer> poolBCount = JsonPath.read(json, "$..pools[?(@.id == '" + poolBId + "')].participationCount");
        List<String> poolAMode = JsonPath.read(json, "$..pools[?(@.id == '" + poolAId + "')].tournamentMode");
        assertThat(poolACount).containsExactly(2);
        assertThat(poolBCount).containsExactly(0);
        assertThat(poolAMode).containsExactly("round robin");
    }

    @Test
    void structure_unknownCompetition_isNotFound() throws Exception {
        mockMvc.perform(get("/v1/competitions/does-not-exist/structure"))
            .andExpect(status().isNotFound());
    }

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
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "Bayernliga", "seasonId": "%s"}
                    """, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createDiscipline(String competitionId) throws Exception {
        String categoryId = createCategory();
        MvcResult result = mockMvc.perform(post("/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "Herren", "competitionId": "%s", "categoryId": "%s"}
                    """, competitionId, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createStage(String disciplineId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/stages")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "Hauptrunde", "disciplineId": "%s"}
                    """, disciplineId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createPool(String stageId, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/pools")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "%s", "tournamentMode": "round robin",
                    "stageId": "%s", "poolState": "PLANNED"}
                    """, name, stageId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTeam() throws Exception {
        String clubId = createClub();
        MvcResult result = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                    {"name": "TFC München", "clubId": "%s"}
                    """, clubId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void createParticipation(String competitionId, String poolId) throws Exception {
        String teamId = createTeam();
        String body = poolId == null
            ? String.format("{\"teamId\": \"%s\", \"competitionId\": \"%s\"}", teamId, competitionId)
            : String.format("{\"teamId\": \"%s\", \"competitionId\": \"%s\", \"poolId\": \"%s\"}",
                teamId, competitionId, poolId);
        mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }
    //endregion
}
