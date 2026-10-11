package de.dtfb.sportshub.backend.api;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import de.dtfb.sportshub.backend.support.TestIds;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The list filters of the structure endpoints (SPO-120): every filter narrows, and they combine with AND.
 * Two federations, each with one season, league, tier, group, round, club and team in its league.
 */
class ListFilterIntegrationTest extends AuthorizedControllerTest {

    private Tree a;
    private Tree b;

    @BeforeEach
    void setUp() throws Exception {
        a = new Tree();
        b = new Tree();
    }

    @Test
    void seasonsAndLeagues() throws Exception {
        list("/v1/seasons", "federationId", a.federationId).andExpect(ids(Matchers.contains(a.seasonId)));
        list("/v1/leagues", "federationId", a.federationId).andExpect(ids(Matchers.contains(a.leagueId)));
        list("/v1/leagues", "seasonId", b.seasonId).andExpect(ids(Matchers.contains(b.leagueId)));
        mockMvc.perform(get("/v1/leagues").param("federationId", a.federationId).param("seasonId", b.seasonId))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void tiersGroupsAndRounds() throws Exception {
        list("/v1/tiers", "leagueId", a.leagueId).andExpect(ids(Matchers.contains(a.tierId)));
        list("/v1/tiers", "seasonId", b.seasonId).andExpect(ids(Matchers.contains(b.tierId)));
        list("/v1/groups", "leagueId", a.leagueId).andExpect(ids(Matchers.hasItem(a.groupId)))
            .andExpect(jsonPath("$[*].tierId").value(Matchers.everyItem(Matchers.is(a.tierId))));
        list("/v1/groups", "seasonId", a.seasonId).andExpect(ids(Matchers.not(Matchers.hasItem(b.groupId))));
        list("/v1/rounds", "groupId", a.groupId).andExpect(ids(Matchers.contains(a.roundId)));
        list("/v1/rounds", "leagueId", b.leagueId).andExpect(ids(Matchers.contains(b.roundId)));
        mockMvc.perform(get("/v1/rounds").param("groupId", a.groupId).param("leagueId", b.leagueId))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void teamsAndParticipations() throws Exception {
        list("/v1/teams", "clubId", a.clubId).andExpect(ids(Matchers.contains(a.teamId)));
        list("/v1/teams", "federationId", b.federationId).andExpect(ids(Matchers.contains(b.teamId)));
        mockMvc.perform(get("/v1/teams").param("seasonId", a.seasonId).param("clubId", b.clubId))
            .andExpect(jsonPath("$").isEmpty());

        list("/v1/team-participations", "leagueId", a.leagueId)
            .andExpect(jsonPath("$[*].teamId").value(Matchers.contains(a.teamId)));
        // AND, not "the first filter given" as before
        mockMvc.perform(get("/v1/team-participations").param("leagueId", a.leagueId).param("teamId", b.teamId))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void clubs() throws Exception {
        list("/v1/clubs", "federationId", a.federationId).andExpect(ids(Matchers.contains(a.clubId)));
        list("/v1/clubs", "q", a.clubName.substring(3).toUpperCase()).andExpect(ids(Matchers.contains(a.clubId)));
        mockMvc.perform(get("/v1/clubs").param("federationId", b.federationId).param("q", a.clubName))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/v1/clubs").param("federationId", a.federationId).param("active", "false"))
            .andExpect(jsonPath("$").isEmpty());
    }

    //region helpers
    private ResultActions list(String path, String param, String value) throws Exception {
        return mockMvc.perform(get(path).param(param, value)).andExpect(status().isOk());
    }

    private static org.springframework.test.web.servlet.ResultMatcher ids(org.hamcrest.Matcher<?> matcher) {
        return jsonPath("$[*].id").value(matcher);
    }

    /** One federation's chain: season → league → tier → group → round, plus a club and its team in the league. */
    private final class Tree {
        final String federationId = createFederation();
        final String seasonId = createSeasonId(federationId);
        final String leagueId = create("/v1/leagues",
            "{\"name\": \"Liga\", \"seasonId\": \"" + seasonId + "\", \"categoryId\": \"" + createCategory() + "\"}");
        final String tierId = create("/v1/tiers", "{\"name\": \"Stufe\", \"leagueId\": \"" + leagueId + "\"}");
        final String groupId = create("/v1/groups",
            "{\"name\": \"Gruppe\", \"tierId\": \"" + tierId + "\", \"groupState\": \"READY\"}");
        final String roundId = create("/v1/rounds",
            "{\"name\": \"Runde\", \"index\": 1, \"groupId\": \"" + groupId + "\"}");
        final String clubName = TestIds.unique("Filterverein");
        final String clubId = create("/v1/clubs",
            "{\"name\": \"" + clubName + "\", \"active\": true, \"regionId\": \"" + federationId + "\"}");
        final String teamId = create("/v1/teams",
            "{\"name\": \"Team\", \"clubId\": \"" + clubId + "\", \"seasonId\": \"" + seasonId + "\"}");

        Tree() throws Exception {
            create("/v1/team-participations", "{\"teamId\": \"" + teamId + "\", \"leagueId\": \"" + leagueId + "\"}");
        }
    }

    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
    //endregion
}
