package de.dtfb.sportshub.backend.roster;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Roster CRUD + lifecycle as an admin (real authz stack, admin rights). Exercises the hardwired
 * hard rules: edits require DRAFT + open registration; the lifecycle transitions guard their status.
 * Uses seeded players ({@code player-test}, {@code player-club}).
 */
class RosterControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private static final String PLAYER_A = "player-test";
    private static final String PLAYER_B = "player-club";

    private String openParticipationId;   // participation under a registration-open season
    private String closedParticipationId; // participation under a registration-closed season

    @PostConstruct
    void setup() throws Exception {
        String federationId = createFederation();
        openParticipationId = participationUnderSeason(federationId, true);
        closedParticipationId = participationUnderSeason(federationId, false);
    }

    private String rosterUrl(String participationId) {
        return "/v1/team-participations/" + participationId + "/roster";
    }

    @Test
    void addListRemove_roundtrip() throws Exception {
        String url = rosterUrl(openParticipationId);

        add(url, PLAYER_A).andExpect(status().isCreated());
        add(url, PLAYER_B).andExpect(status().isCreated());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));

        // duplicate active player is rejected
        add(url, PLAYER_A).andExpect(status().isConflict());

        mockMvc.perform(delete(url + "/" + PLAYER_A)).andExpect(status().isOk());
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        // removing someone not on the roster → 404
        mockMvc.perform(delete(url + "/" + PLAYER_A)).andExpect(status().isNotFound());
    }

    @Test
    void lifecycle_submitConfirmReopen() throws Exception {
        String url = rosterUrl(openParticipationId);
        add(url, PLAYER_A).andExpect(status().isCreated());

        mockMvc.perform(post(url + "/submit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterStatus").value("SUBMITTED"));

        // roster is locked once submitted
        add(url, PLAYER_B).andExpect(status().isConflict());

        mockMvc.perform(post(url + "/confirm"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterStatus").value("CONFIRMED"));

        mockMvc.perform(post(url + "/reopen"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterStatus").value("DRAFT"));
    }

    @Test
    void confirm_whenNotSubmitted_isConflict() throws Exception {
        // fresh participation is DRAFT, not SUBMITTED
        mockMvc.perform(post(rosterUrl(openParticipationId) + "/confirm"))
            .andExpect(status().isConflict());
    }

    @Test
    void edit_whenRegistrationClosed_isConflict() throws Exception {
        add(rosterUrl(closedParticipationId), PLAYER_A).andExpect(status().isConflict());
    }

    @Test
    void edit_whenWithdrawn_isConflict() throws Exception {
        mockMvc.perform(post("/v1/team-participations/" + openParticipationId + "/withdraw"))
            .andExpect(status().isOk());

        add(rosterUrl(openParticipationId), PLAYER_A).andExpect(status().isConflict());
    }

    @Test
    void submit_whenWithdrawn_isConflict() throws Exception {
        add(rosterUrl(openParticipationId), PLAYER_A).andExpect(status().isCreated());
        mockMvc.perform(post("/v1/team-participations/" + openParticipationId + "/withdraw"))
            .andExpect(status().isOk());

        mockMvc.perform(post(rosterUrl(openParticipationId) + "/submit")).andExpect(status().isConflict());
    }

    @Test
    void addPlayer_beyondMaxRosterSize_isConflict() throws Exception {
        String federationId = createFederation();
        String ruleSetId = createRuleSet(federationId, 1, 2);
        String url = rosterUrl(participationUnderLeague(federationId, true, ruleSetId));

        add(url, "player-p1").andExpect(status().isCreated());
        add(url, "player-p2").andExpect(status().isCreated());
        add(url, "player-p3")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROSTER_AT_MAX"))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.current").value(2));

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void submit_belowMinRosterSize_isConflict() throws Exception {
        String federationId = createFederation();
        String ruleSetId = createRuleSet(federationId, 2, null);
        String url = rosterUrl(participationUnderLeague(federationId, true, ruleSetId));

        add(url, "player-p1").andExpect(status().isCreated());
        mockMvc.perform(post(url + "/submit"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ROSTER_BELOW_MIN"))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.current").value(1));

        add(url, "player-p2").andExpect(status().isCreated());
        mockMvc.perform(post(url + "/submit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rosterStatus").value("SUBMITTED"));
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private org.springframework.test.web.servlet.ResultActions add(String url, String playerId) throws Exception {
        return mockMvc.perform(post(url)
            .contentType(MediaType.APPLICATION_JSON)
            .content(String.format("{\"playerId\": \"%s\"}", playerId)));
    }

    /** Create a season (open or closed) with a league + team, and return a participation id. */
    private String participationUnderSeason(String federationId, boolean registrationOpen) throws Exception {
        return participationUnderLeague(federationId, registrationOpen, null);
    }

    /** Same as above, but the league uses the given rule set (for roster-size enforcement tests). */
    private String participationUnderLeague(String federationId, boolean registrationOpen, String ruleSetId)
            throws Exception {
        MvcResult season = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "S", "federationId": "%s", "registrationOpen": %s}
                    """, federationId, registrationOpen)))
            .andExpect(status().isCreated())
            .andReturn();
        String seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");

        MvcResult league = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Liga", "seasonId": "%s", "categoryId": "%s", "ruleSetId": %s}
                    """, seasonId, createCategory(), ruleSetId == null ? "null" : "\"" + ruleSetId + "\"")))
            .andExpect(status().isCreated())
            .andReturn();
        String leagueId = JsonPath.read(league.getResponse().getContentAsString(), "$.id");

        MvcResult team = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"Team\", \"clubId\": \"%s\"}", createClub())))
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

    /** Create a league rule set with the given roster-size bounds (either may be null). */
    private String createRuleSet(String federationId, Integer minRosterSize, Integer maxRosterSize) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/league-rule-sets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "RS", "federationId": "%s", "minRosterSize": %s, "maxRosterSize": %s}
                    """, federationId, minRosterSize, maxRosterSize)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    //endregion
}
