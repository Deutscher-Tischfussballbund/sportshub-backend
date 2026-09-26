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

    @Test
    void createLeague_copiesTheBlueprintIntoItsOwnSnapshot() throws Exception {
        String blueprintId = idFromUrl(url);
        String leagueJson = createLeague(id(createSeason(federationId)), createCategory(), blueprintId)
            .getResponse().getContentAsString();
        String snapshotId = JsonPath.read(leagueJson, "$.ruleSetId");

        org.assertj.core.api.Assertions.assertThat(snapshotId).isNotEqualTo(blueprintId);
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(leagueJson, "$.blueprintId"))
            .isEqualTo(blueprintId);
        mockMvc.perform(get("/v1/league-rule-sets/" + snapshotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.snapshot").value(true))
            .andExpect(jsonPath("$.sourceBlueprintId").value(blueprintId))
            .andExpect(jsonPath("$.pointsWin").value(3))
            .andExpect(jsonPath("$.gamePlan.length()").value(3));

        // the library lists blueprints only
        String all = mockMvc.perform(get("/v1/league-rule-sets")).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(all).doesNotContain(snapshotId).contains(blueprintId);
    }

    @Test
    void editingTheBlueprint_neverChangesAnExistingLeaguesRules() throws Exception {
        String blueprintId = idFromUrl(url);
        String snapshotId = JsonPath.read(createLeague(id(createSeason(federationId)), createCategory(), blueprintId)
            .getResponse().getContentAsString(), "$.ruleSetId");

        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 2:0", "federationId": "%s",
                     "playSystem": "ROUND_ROBIN", "pointsWin": 2, "pointsLoss": 0}
                    """, federationId)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/league-rule-sets/" + snapshotId))
            .andExpect(jsonPath("$.pointsWin").value(3))
            .andExpect(jsonPath("$.gamePlan.length()").value(3));
    }

    @Test
    void editSnapshot_whileSeasonRuns_isAllowed() throws Exception {
        String snapshotId = JsonPath.read(createLeague(id(createSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString(), "$.ruleSetId");

        mockMvc.perform(put("/v1/league-rule-sets/" + snapshotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Bayernliga-Regeln", "playSystem": "ROUND_ROBIN",
                     "pointsWin": 2, "pointsDraw": 1, "pointsLoss": 0}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointsWin").value(2))
            .andExpect(jsonPath("$.frozen").value(false));
    }

    @Test
    void editSnapshot_afterSeasonEnded_isFrozen_butRenameIsAllowed() throws Exception {
        String snapshotId = JsonPath.read(createLeague(id(createEndedSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString(), "$.ruleSetId");
        mockMvc.perform(get("/v1/league-rule-sets/" + snapshotId)).andExpect(jsonPath("$.frozen").value(true));

        mockMvc.perform(put("/v1/league-rule-sets/" + snapshotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Standard 3:1", "playSystem": "ROUND_ROBIN",
                     "pointsWin": 2, "pointsDraw": 1, "pointsLoss": 0}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_FROZEN"));

        mockMvc.perform(put("/v1/league-rule-sets/" + snapshotId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Renamed only", "playSystem": "ROUND_ROBIN",
                     "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0,
                     "setsPerGame": 3, "pointsToWinSet": 7,
                     "matchdayDecision": "ALL_GAMES", "sideSwitchAllowed": true,
                     "gamePlan": [
                       {"position": 1, "gameType": "DOUBLE"},
                       {"position": 2, "gameType": "DOUBLE"},
                       {"position": 3, "gameType": "SINGLE"}
                     ]}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Renamed only"));
    }

    @Test
    void deleteSnapshot_directly_isConflict() throws Exception {
        String leagueId = id(createLeague(id(createSeason(federationId)), createCategory(), null));
        String tierSnapshotId = JsonPath.read(createTier(leagueId, idFromUrl(url))
            .getResponse().getContentAsString(), "$.ruleSetId");

        mockMvc.perform(delete("/v1/league-rule-sets/" + tierSnapshotId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_IN_USE"));
    }

    @Test
    void deleteBlueprint_usedByALeague_isAllowed_andTheLeagueKeepsItsRules() throws Exception {
        String leagueJson = createLeague(id(createSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString();
        String snapshotId = JsonPath.read(leagueJson, "$.ruleSetId");

        mockMvc.perform(delete(url)).andExpect(status().isOk());

        mockMvc.perform(get("/v1/league-rule-sets/" + snapshotId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointsWin").value(3))
            .andExpect(jsonPath("$.sourceBlueprintId").doesNotExist());
    }

    @Test
    void deleteLeagueRuleSet_blockedByFederationDefault() throws Exception {
        String ruleSetId = idFromUrl(url);
        mockMvc.perform(put("/v1/federations/" + federationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testverband", "defaultRuleSetId": "%s"}
                    """, ruleSetId)))
            .andExpect(status().isOk());

        mockMvc.perform(delete(url))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_IN_USE"));
    }

    @Test
    void league_withoutBlueprint_getsTheFederationDefault() throws Exception {
        String blueprintId = idFromUrl(url);
        mockMvc.perform(put("/v1/federations/" + federationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testverband", "defaultRuleSetId": "%s"}
                    """, blueprintId)))
            .andExpect(status().isOk());

        String leagueJson = createLeague(id(createSeason(federationId)), createCategory(), null)
            .getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat((String) JsonPath.read(leagueJson, "$.blueprintId"))
            .isEqualTo(blueprintId);
    }

    @Test
    void updateLeague_withAnotherBlueprint_resetsItsRules_butNotAfterTheSeasonEnded() throws Exception {
        String otherBlueprintId = idFromUrl(createRuleSet(federationId, 2).getResponse().getHeader("Location"));
        String categoryId = createCategory();

        String running = createLeague(id(createSeason(federationId)), categoryId, idFromUrl(url))
            .getResponse().getContentAsString();
        mockMvc.perform(put("/v1/leagues/" + JsonPath.read(running, "$.id"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s", "blueprintId": "%s"}
                    """, (String) JsonPath.read(running, "$.seasonId"), categoryId, otherBlueprintId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.blueprintId").value(otherBlueprintId))
            .andExpect(jsonPath("$.ruleSetId").value((String) JsonPath.read(running, "$.ruleSetId")));
        mockMvc.perform(get("/v1/league-rule-sets/" + JsonPath.read(running, "$.ruleSetId")))
            .andExpect(jsonPath("$.pointsWin").value(2));

        String ended = createLeague(id(createEndedSeason(federationId)), categoryId, idFromUrl(url))
            .getResponse().getContentAsString();
        mockMvc.perform(put("/v1/leagues/" + JsonPath.read(ended, "$.id"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s", "blueprintId": "%s"}
                    """, (String) JsonPath.read(ended, "$.seasonId"), categoryId, otherBlueprintId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_FROZEN"));
    }

    @Test
    void tierOverride_canBeAddedAndRemoved_andRemovalDeletesItsSnapshot() throws Exception {
        String leagueId = id(createLeague(id(createSeason(federationId)), createCategory(), null));
        String tierJson = createTier(leagueId, null).getResponse().getContentAsString();
        String tierId = JsonPath.read(tierJson, "$.id");
        org.assertj.core.api.Assertions.assertThat((Object) JsonPath.read(tierJson, "$.ruleSetId")).isNull();

        String withOverride = mockMvc.perform(put("/v1/tiers/" + tierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "1. Bayernliga", "leagueId": "%s", "blueprintId": "%s"}
                    """, leagueId, idFromUrl(url))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String overrideId = JsonPath.read(withOverride, "$.ruleSetId");
        org.assertj.core.api.Assertions.assertThat(overrideId).isNotNull();

        // echoing the override keeps it
        mockMvc.perform(put("/v1/tiers/" + tierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "1. Bayernliga (neu)", "leagueId": "%s", "ruleSetId": "%s", "blueprintId": "%s"}
                    """, leagueId, overrideId, idFromUrl(url))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleSetId").value(overrideId));

        // neither id nor blueprint removes it
        mockMvc.perform(put("/v1/tiers/" + tierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "1. Bayernliga", "leagueId": "%s"}
                    """, leagueId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ruleSetId").doesNotExist());
        mockMvc.perform(get("/v1/league-rule-sets/" + overrideId)).andExpect(status().isNotFound());
    }

    @Test
    void deleteLeague_deletesItsSnapshot() throws Exception {
        String leagueJson = createLeague(id(createSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString();

        mockMvc.perform(delete("/v1/leagues/" + JsonPath.read(leagueJson, "$.id"))).andExpect(status().isOk());

        mockMvc.perform(get("/v1/league-rule-sets/" + JsonPath.read(leagueJson, "$.ruleSetId")))
            .andExpect(status().isNotFound());
    }

    @Test
    void participationRules_areTheLeaguesRules_orTheTierOverrideOnceTheTeamIsPlaced() throws Exception {
        String seasonId = id(createSeason(federationId));
        String leagueJson = createLeague(seasonId, createCategory(), idFromUrl(url))
            .getResponse().getContentAsString();
        String leagueId = JsonPath.read(leagueJson, "$.id");
        String otherBlueprintId = idFromUrl(createRuleSet(federationId, 2).getResponse().getHeader("Location"));
        String tierId = JsonPath.read(createTier(leagueId, otherBlueprintId).getResponse().getContentAsString(), "$.id");
        String groupId = JsonPath.read(mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Gesamt", "tierId": "%s", "groupState": "PLANNED"}
                    """, tierId)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String teamId = JsonPath.read(mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "TFC", "clubId": "%s", "seasonId": "%s"}
                    """, createClub(federationId), seasonId)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String participationId = JsonPath.read(mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, leagueId)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        // not placed yet: the league's own rules
        mockMvc.perform(get("/v1/team-participations/" + participationId + "/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value((String) JsonPath.read(leagueJson, "$.ruleSetId")))
            .andExpect(jsonPath("$.pointsWin").value(3))
            .andExpect(jsonPath("$.gamePlan.length()").value(3));

        // placed in the tier with its own rules: those apply
        mockMvc.perform(put("/v1/team-participations/" + participationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"teamId": "%s", "leagueId": "%s", "groupId": "%s"}
                    """, teamId, leagueId, groupId)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/v1/team-participations/" + participationId + "/rules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pointsWin").value(2))
            .andExpect(jsonPath("$.snapshot").value(true));
    }

    @Test
    void participationRules_ofUnknownParticipation_isNotFound() throws Exception {
        mockMvc.perform(get("/v1/team-participations/" + NanoIdUtils.randomNanoId() + "/rules"))
            .andExpect(status().isNotFound());
    }

    @Test
    void templateName_isUniquePerFederation_caseInsensitive() throws Exception {
        String existingId = idFromUrl(url);

        mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "standard 3:1", "federationId": "%s", "playSystem": "ROUND_ROBIN"}
                    """, federationId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_NAME_TAKEN"))
            .andExpect(jsonPath("$.existingId").value(existingId));

        // another federation may use the same name
        mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 3:1", "federationId": "%s", "playSystem": "ROUND_ROBIN"}
                    """, createFederation())))
            .andExpect(status().isCreated());
    }

    @Test
    void renamingATemplate_toATakenName_isConflict_butKeepingItsOwnNameIsFine() throws Exception {
        String otherUrl = createRuleSet(federationId, 2).getResponse().getHeader("Location");

        mockMvc.perform(put(otherUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 3:1", "federationId": "%s", "playSystem": "ROUND_ROBIN"}
                    """, federationId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_NAME_TAKEN"));

        mockMvc.perform(put(otherUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 2:1", "federationId": "%s", "playSystem": "ROUND_ROBIN", "pointsWin": 5}
                    """, federationId)))
            .andExpect(status().isOk());
    }

    @Test
    void cloningTwice_numbersTheCopies() throws Exception {
        mockMvc.perform(post(url + "/clone"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Standard 3:1 (Kopie)"));
        mockMvc.perform(post(url + "/clone"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Standard 3:1 (Kopie 2)"));
    }

    @Test
    void snapshots_mayShareATemplatesName() throws Exception {
        String categoryId = createCategory();
        String first = JsonPath.read(createLeague(id(createSeason(federationId)), categoryId, idFromUrl(url))
            .getResponse().getContentAsString(), "$.ruleSetId");
        createLeague(id(createSeason(federationId)), categoryId, idFromUrl(url));

        mockMvc.perform(get("/v1/league-rule-sets/" + first))
            .andExpect(jsonPath("$.name").value("Standard 3:1"));
    }

    @Test
    void deleteSeason_deletesItsLeaguesAndTiersSnapshots() throws Exception {
        String seasonId = id(createSeason(federationId));
        String leagueJson = createLeague(seasonId, createCategory(), idFromUrl(url))
            .getResponse().getContentAsString();
        String tierJson = createTier(JsonPath.read(leagueJson, "$.id"), idFromUrl(url))
            .getResponse().getContentAsString();

        mockMvc.perform(delete("/v1/seasons/" + seasonId)).andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/v1/league-rule-sets/" + JsonPath.read(leagueJson, "$.ruleSetId")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/v1/league-rule-sets/" + JsonPath.read(tierJson, "$.ruleSetId")))
            .andExpect(status().isNotFound());
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void applyBlueprint_overwritesRunningLeagues_andRefusesEndedOnes() throws Exception {
        String categoryId = createCategory();
        String leagueJson = createLeague(id(createSeason(federationId)), categoryId, idFromUrl(url))
            .getResponse().getContentAsString();
        String otherBlueprintId = idFromUrl(createRuleSet(federationId, 2).getResponse().getHeader("Location"));

        mockMvc.perform(post("/v1/league-rule-sets/" + otherBlueprintId + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"leagueIds": ["%s"]}
                    """, (String) JsonPath.read(leagueJson, "$.id"))))
            .andExpect(status().isNoContent());
        mockMvc.perform(get("/v1/league-rule-sets/" + JsonPath.read(leagueJson, "$.ruleSetId")))
            .andExpect(jsonPath("$.pointsWin").value(2))
            .andExpect(jsonPath("$.sourceBlueprintId").value(otherBlueprintId));

        String endedJson = createLeague(id(createEndedSeason(federationId)), categoryId, idFromUrl(url))
            .getResponse().getContentAsString();
        mockMvc.perform(post("/v1/league-rule-sets/" + otherBlueprintId + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"leagueIds": ["%s"]}
                    """, (String) JsonPath.read(endedJson, "$.id"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RULE_SET_FROZEN"));
    }

    @Test
    void applyBlueprint_withASnapshotId_isBadRequest() throws Exception {
        String leagueJson = createLeague(id(createSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString();

        mockMvc.perform(post("/v1/league-rule-sets/" + JsonPath.read(leagueJson, "$.ruleSetId") + "/apply")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"leagueIds": ["%s"]}
                    """, (String) JsonPath.read(leagueJson, "$.id"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void archivedBlueprint_isListedWithItsFlag() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 3:1", "federationId": "%s", "archived": true,
                     "playSystem": "ROUND_ROBIN", "pointsWin": 3, "pointsDraw": 1, "pointsLoss": 0}
                    """, federationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true));
    }

    @Test
    void cloneSnapshot_createsAnEditableBlueprint() throws Exception {
        String snapshotId = JsonPath.read(createLeague(id(createEndedSeason(federationId)), createCategory(), idFromUrl(url))
            .getResponse().getContentAsString(), "$.ruleSetId");

        MvcResult cloneResult = mockMvc.perform(post("/v1/league-rule-sets/" + snapshotId + "/clone"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Standard 3:1 (Kopie)"))
            .andExpect(jsonPath("$.snapshot").value(false))
            .andReturn();
        String clonedUrl = cloneResult.getResponse().getHeader("Location");
        assert clonedUrl != null;

        mockMvc.perform(put(clonedUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Standard 2:0", "federationId": "%s",
                     "playSystem": "ROUND_ROBIN", "pointsWin": 2, "pointsDraw": 1, "pointsLoss": 0}
                    """, federationId)))
            .andExpect(status().isOk());
    }

    //region helpers
    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String idFromUrl(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private MvcResult createSeason(String federationId) throws Exception {
        return mockMvc.perform(post("/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                {"name": "2025", "federationId": "%s"}
                """, federationId))).andReturn();
    }

    private MvcResult createEndedSeason(String federationId) throws Exception {
        return mockMvc.perform(post("/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                {"name": "2020", "federationId": "%s", "startDate": "2019-09-01", "endDate": "2020-05-31"}
                """, federationId))).andReturn();
    }

    private MvcResult createLeague(String seasonId, String categoryId, String blueprintId) throws Exception {
        String blueprint = blueprintId == null ? "null" : "\"" + blueprintId + "\"";
        return mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s", "blueprintId": %s}
                    """, seasonId, categoryId, blueprint)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createTier(String leagueId, String blueprintId) throws Exception {
        String blueprint = blueprintId == null ? "null" : "\"" + blueprintId + "\"";
        return mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "1. Bayernliga", "leagueId": "%s", "blueprintId": %s}
                    """, leagueId, blueprint)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createRuleSet(String federationId) throws Exception {
        return createRuleSet(federationId, 3);
    }

    private MvcResult createRuleSet(String federationId, int pointsWin) throws Exception {
        return mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "%s", "federationId": "%s",
                     "playSystem": "ROUND_ROBIN",
                     "pointsWin": %d, "pointsDraw": 1, "pointsLoss": 0,
                     "setsPerGame": 3, "pointsToWinSet": 7,
                     "matchdayDecision": "ALL_GAMES", "sideSwitchAllowed": true,
                     "gamePlan": [
                       {"position": 1, "gameType": "DOUBLE"},
                       {"position": 2, "gameType": "DOUBLE"},
                       {"position": 3, "gameType": "SINGLE"}
                     ]}
                    """, pointsWin == 3 ? "Standard 3:1" : "Standard " + pointsWin + ":1", federationId, pointsWin)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
