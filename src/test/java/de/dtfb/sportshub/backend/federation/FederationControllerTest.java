package de.dtfb.sportshub.backend.federation;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FederationControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    @Test
    void updateFederationDefaultRuleSet_blockedByDependentTierWithFixtures() throws Exception {
        String federationId = createFederation();
        String ruleSetA = createRuleSet(federationId);
        String ruleSetB = createRuleSet(federationId);

        updateFederationDefault(federationId, ruleSetA).andExpect(status().isOk());

        String seasonId = createSeason(federationId);
        String leagueId = createLeague(seasonId, null);
        String tierId = createTier(leagueId, null);
        String groupId = createGroup(tierId);
        createRound(groupId);

        updateFederationDefault(federationId, ruleSetB)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("FEDERATION_DEFAULT_HAS_RUNNING_LEAGUES"));
    }

    @Test
    void updateFederationDefaultRuleSet_notBlockedWhenTierHasExplicitOverride() throws Exception {
        String federationId = createFederation();
        String ruleSetA = createRuleSet(federationId);
        String ruleSetB = createRuleSet(federationId);

        updateFederationDefault(federationId, ruleSetA).andExpect(status().isOk());

        String seasonId = createSeason(federationId);
        String leagueId = createLeague(seasonId, null);
        String tierId = createTier(leagueId, ruleSetA);
        String groupId = createGroup(tierId);
        createRound(groupId);

        updateFederationDefault(federationId, ruleSetB).andExpect(status().isOk());
    }

    @Test
    void updateFederationDefaultRuleSet_notBlockedWhenNoFixturesYet() throws Exception {
        String federationId = createFederation();
        String ruleSetA = createRuleSet(federationId);
        String ruleSetB = createRuleSet(federationId);

        updateFederationDefault(federationId, ruleSetA).andExpect(status().isOk());

        String seasonId = createSeason(federationId);
        String leagueId = createLeague(seasonId, null);
        createTier(leagueId, null);

        updateFederationDefault(federationId, ruleSetB).andExpect(status().isOk());
    }

    //region helpers
    private org.springframework.test.web.servlet.ResultActions updateFederationDefault(
        String federationId, String ruleSetId) throws Exception {
        return mockMvc.perform(put("/v1/federation/" + federationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("""
                {"name": "Testverband", "defaultRuleSetId": "%s"}
                """, ruleSetId)));
    }

    private String createRuleSet(String federationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testregelwerk", "federationId": "%s", "playSystem": "ROUND_ROBIN",
                     "pointsWin": 2, "pointsDraw": 1, "pointsLoss": 0}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createSeason(String federationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "2025", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createLeague(String seasonId, String ruleSetId) throws Exception {
        String categoryId = createCategory();
        String ruleSetField = ruleSetId == null ? "" : String.format(", \"ruleSetId\": \"%s\"", ruleSetId);
        MvcResult result = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s"%s}
                    """, seasonId, categoryId, ruleSetField)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createTier(String leagueId, String ruleSetId) throws Exception {
        String ruleSetField = ruleSetId == null ? "" : String.format(", \"ruleSetId\": \"%s\"", ruleSetId);
        MvcResult result = mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "1. Bayernliga", "leagueId": "%s"%s}
                    """, leagueId, ruleSetField)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createGroup(String tierId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Gruppe A", "tierId": "%s", "groupState": "PLANNED"}
                    """, tierId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createRound(String groupId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Runde 1", "groupId": "%s"}
                    """, groupId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    //endregion
}
