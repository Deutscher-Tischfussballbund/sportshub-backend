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
    private String competitionId;
    private String poolId;
    private String teamId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");
        MvcResult competition = createEvent(seasonId);
        competitionId = JsonPath.read(competition.getResponse().getContentAsString(), "$.id");
        MvcResult discipline = createDiscipline(competitionId);
        String disciplineId = JsonPath.read(discipline.getResponse().getContentAsString(), "$.id");
        MvcResult stage = createStage(disciplineId);
        String stageId = JsonPath.read(stage.getResponse().getContentAsString(), "$.id");
        MvcResult pool = createPool(stageId);
        poolId = JsonPath.read(pool.getResponse().getContentAsString(), "$.id");
        MvcResult team = createTeam("Hand und Foos");
        teamId = JsonPath.read(team.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        // Add a team to the competition without a division yet (registered-but-unplaced).
        MvcResult participation = createParticipation(null);
        url = participation.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllParticipations() throws Exception {
        mockMvc.perform(get("/v1/team-participations"))
            .andExpect(status().isOk());
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
            .andExpect(jsonPath("$.competitionId").value(competitionId))
            // season is derived from the competition, not sent by the client
            .andExpect(jsonPath("$.seasonId").value(seasonId));
    }

    @Test
    void updateParticipation_placesIntoPool() throws Exception {
        // promote/relegate: move the team into a division
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "competitionId": "%s", "poolId": "%s"}
                    """, teamId, competitionId, poolId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.poolId").value(poolId));
    }

    @Test
    void updateParticipation_expectException() throws Exception {
        mockMvc.perform(put("/v1/team-participations/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "competitionId": "%s"}
                    """, teamId, competitionId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation_expectException() throws Exception {
        mockMvc.perform(delete("/v1/team-participations/" + NanoIdUtils.randomNanoId()))
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

    private MvcResult createEvent(String uuid) throws Exception {
        return mockMvc.perform(post("/v1/competitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier", "seasonId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createDiscipline(String uuid) throws Exception {
        String categoryId = createCategory();
        return mockMvc.perform(post("/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Offenes Einzel", "competitionId": "%s", "categoryId": "%s"}
                    """, uuid, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createStage(String uuid) throws Exception {
        return mockMvc.perform(post("/v1/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Vorrunde", "disciplineId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createPool(String uuid) throws Exception {
        return mockMvc.perform(post("/v1/pools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bundesliga", "tournamentMode": "round robin",
                            "stageId": "%s", "poolState": "PLANNED"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createTeam(String name) throws Exception {
        String clubId = createClub();
        return mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s", "clubId": "%s"}
                    """, name, clubId)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createParticipation(String poolId) throws Exception {
        String pool = poolId == null ? "null" : "\"" + poolId + "\"";
        return mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "competitionId": "%s", "poolId": %s}
                    """, teamId, competitionId, pool)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
