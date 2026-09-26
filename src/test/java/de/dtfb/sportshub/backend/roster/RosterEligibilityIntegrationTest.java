package de.dtfb.sportshub.backend.roster;

import de.dtfb.sportshub.backend.support.TestIds;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Category eligibility (docs/19) enforced on roster add and submit. Uses seeded players of known
 * gender: player-p1 (MALE), player-p7 (FEMALE), player-p9 (DIVERSE_WOMEN). Runs as the bootstrap
 * global admin -- eligibility has no admin bypass.
 */
class RosterEligibilityIntegrationTest extends AuthorizedControllerTest {

    private static final String MALE = "player-p1";
    private static final String FEMALE = "player-p7";
    private static final String DIVERSE_WOMEN = "player-p9";

    @Test
    void womensCategory_admitsFemaleAndDiversWomen() throws Exception {
        String url = rosterUrl(participation(createCategory("women"), FEMALE, DIVERSE_WOMEN));

        add(url, FEMALE).andExpect(status().isCreated());
        add(url, DIVERSE_WOMEN).andExpect(status().isCreated());
    }

    @Test
    void womensCategory_rejectsMale_withWrongSide() throws Exception {
        String url = rosterUrl(participation(createCategory("women"), MALE));

        add(url, MALE)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAYER_NOT_ELIGIBLE"))
            .andExpect(jsonPath("$.players[0].playerId").value(MALE))
            .andExpect(jsonPath("$.players[0].reason").value("WRONG_SIDE"));
    }

    @Test
    void openCategory_admitsEveryone() throws Exception {
        String url = rosterUrl(participation(createCategory(null), MALE, FEMALE, DIVERSE_WOMEN));

        add(url, MALE).andExpect(status().isCreated());
        add(url, FEMALE).andExpect(status().isCreated());
        add(url, DIVERSE_WOMEN).andExpect(status().isCreated());
    }

    @Test
    void submit_rechecksWholeRoster_whenCategoryWasRestrictedAfterAdding() throws Exception {
        String categoryId = createCategory(null);
        String url = rosterUrl(participation(categoryId, MALE, FEMALE));
        add(url, MALE).andExpect(status().isCreated());
        add(url, FEMALE).andExpect(status().isCreated());

        restrict(categoryId, "women");

        mockMvc.perform(post(url + "/submit"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PLAYER_NOT_ELIGIBLE"))
            .andExpect(jsonPath("$.players.length()").value(1))
            .andExpect(jsonPath("$.players[0].playerId").value(MALE))
            .andExpect(jsonPath("$.players[0].name").value("Lukas Bauer"));
    }

    //region helpers
    private String rosterUrl(String participationId) {
        return "/v1/team-participations/" + participationId + "/roster";
    }

    private ResultActions add(String url, String playerId) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"playerId\": \"%s\"}", playerId)));
    }

    /** A category with the given eligible side wire value ("men"/"women"), or open when null. */
    private String createCategory(String eligibleSide) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"C\", \"shortName\": \"%s\", \"eligibleSide\": %s}",
                    TestIds.unique("C"), eligibleSide == null ? "null" : "\"" + eligibleSide + "\"")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.eligibleSide").value(eligibleSide))
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void restrict(String categoryId, String eligibleSide) throws Exception {
        mockMvc.perform(put("/v1/categories/" + categoryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"C\", \"shortName\": \"%s\", \"eligibleSide\": \"%s\"}",
                    TestIds.unique("C"), eligibleSide)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.eligibleSide").value(eligibleSide));
    }

    /** An open-registration season -> league (given category) -> team, players joined to its club. */
    private String participation(String categoryId, String... playerIdsToJoinClub) throws Exception {
        String federationId = createFederation();
        MvcResult season = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "S", "federationId": "%s", "registrationOpensAt": "2020-01-01"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        String seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");

        MvcResult league = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"Liga\", \"seasonId\": \"%s\", \"categoryId\": \"%s\"}",
                    seasonId, categoryId)))
            .andExpect(status().isCreated())
            .andReturn();
        String leagueId = JsonPath.read(league.getResponse().getContentAsString(), "$.id");

        String clubId = createClub(federationId);
        for (String playerId : playerIdsToJoinClub) {
            joinClub(playerId, clubId);
        }

        MvcResult team = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"Team\", \"clubId\": \"%s\", \"seasonId\": \"%s\"}",
                    clubId, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        String teamId = JsonPath.read(team.getResponse().getContentAsString(), "$.id");

        MvcResult participation = mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"teamId\": \"%s\", \"leagueId\": \"%s\"}", teamId, leagueId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(participation.getResponse().getContentAsString(), "$.id");
    }
    //endregion
}
