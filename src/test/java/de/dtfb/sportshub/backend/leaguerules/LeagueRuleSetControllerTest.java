package de.dtfb.sportshub.backend.leaguerules;

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

class LeagueRuleSetControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String federationId;
    String url;

    @PostConstruct
    void setup() throws Exception {
        federationId = createFederation();
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult ruleSet = createRuleSet(federationId);
        url = ruleSet.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllLeagueRuleSets() throws Exception {
        mockMvc.perform(get("/v1/league-rule-sets"))
            .andExpect(status().isOk());
    }

    @Test
    void getLeagueRuleSet_expectException() throws Exception {
        mockMvc.perform(get("/v1/league-rule-sets/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetLeagueRuleSet_withGamePlan() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Standard 3:1"))
            .andExpect(jsonPath("$.playSystem").value("ROUND_ROBIN"))
            .andExpect(jsonPath("$.pointsWin").value(3))
            .andExpect(jsonPath("$.gamePlan.length()").value(3))
            .andExpect(jsonPath("$.gamePlan[0].position").value(1))
            .andExpect(jsonPath("$.gamePlan[0].gameType").value("DOUBLE"))
            .andExpect(jsonPath("$.gamePlan[2].gameType").value("SINGLE"));
    }

    @Test
    void createGlobalTemplate_withoutFederation() throws Exception {
        mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "DTFB default", "playSystem": "ROUND_ROBIN"}
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.federationId").doesNotExist());
    }

    @Test
    void updateLeagueRuleSet_replacesGamePlan() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 2:0", "federationId": "%s",
                     "playSystem": "ROUND_ROBIN", "pointsWin": 2, "pointsLoss": 0,
                     "gamePlan": [{"position": 1, "gameType": "SINGLE"}]}
                    """, federationId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Standard 2:0"))
            .andExpect(jsonPath("$.pointsWin").value(2))
            .andExpect(jsonPath("$.gamePlan.length()").value(1))
            .andExpect(jsonPath("$.gamePlan[0].gameType").value("SINGLE"));
    }

    @Test
    void deleteLeagueRuleSet() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteLeagueRuleSet_expectException() throws Exception {
        mockMvc.perform(delete("/v1/league-rule-sets/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    //region helpers
    private MvcResult createRuleSet(String federationId) throws Exception {
        return mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 3:1", "federationId": "%s",
                     "playSystem": "ROUND_ROBIN",
                     "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                     "setsPerGame": 3, "pointsToWinSet": 7,
                     "matchdayDecision": "ALL_GAMES", "sideSwitchAllowed": true,
                     "gamePlan": [
                       {"position": 1, "gameType": "DOUBLE"},
                       {"position": 2, "gameType": "DOUBLE"},
                       {"position": 3, "gameType": "SINGLE"}
                     ]}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
