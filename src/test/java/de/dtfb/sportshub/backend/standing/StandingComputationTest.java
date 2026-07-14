package de.dtfb.sportshub.backend.standing;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 rule enforcement: after a match day is confirmed, {@code StandingService} awards standings
 * points from the group's effective {@link de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet}
 * (the league's here), and falls back to the historical 2/1/0 when no rule set is configured.
 *
 * <p>Drives the real workflow end-to-end: a home win submitted by the home team's admin and
 * confirmed by the away team's admin (a different actor, as the service requires), then reads the
 * standings back.
 */
class StandingComputationTest extends AuthorizedControllerTest {

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Test
    void standings_useLeagueRuleSetPoints() throws Exception {
        Scenario s = buildLeagueWithHomeWin(3, 1, 0);

        assertThat(pointsOf(s.groupId(), s.homeTeamId())).isEqualTo(3);  // win = 3 per the rule set
        assertThat(winsOf(s.groupId(), s.homeTeamId())).isEqualTo(1);
        assertThat(pointsOf(s.groupId(), s.awayTeamId())).isEqualTo(0);  // loss = 0
        assertThat(lossesOf(s.groupId(), s.awayTeamId())).isEqualTo(1);
    }

    @Test
    void standings_fallBackToDefaultsWithoutRuleSet() throws Exception {
        Scenario s = buildLeagueWithHomeWin(null, null, null);

        assertThat(pointsOf(s.groupId(), s.homeTeamId())).isEqualTo(2);  // default win = 2
        assertThat(pointsOf(s.groupId(), s.awayTeamId())).isEqualTo(0);  // default loss = 0
    }

    @Test
    void standings_useFederationDefaultWhenTierAndLeagueHaveNone() throws Exception {
        // A rule set set as the federation's default, with neither tier nor league overriding it.
        String federationId = create("/v1/federation", "{\"name\":\"FedDefault\"}");
        String ruleSetId = createRuleSet(federationId, 5, 2, 1);
        update("/v1/federation/" + federationId,
            "{\"name\":\"FedDefault\",\"defaultRuleSetId\":\"" + ruleSetId + "\"}");

        Scenario s = buildLeagueWithHomeWin(federationId, "");  // league has no rule set

        assertThat(pointsOf(s.groupId(), s.homeTeamId())).isEqualTo(5);  // federation default win = 5
        assertThat(pointsOf(s.groupId(), s.awayTeamId())).isEqualTo(1);  // federation default loss = 1
    }

    /** Build a league (optionally with a points rule set on the league), play one home win, confirm. */
    private Scenario buildLeagueWithHomeWin(Integer win, Integer draw, Integer loss) throws Exception {
        String federationId = create("/v1/federation", "{\"name\":\"Testverband\"}");
        String ruleSetRef = "";
        if (win != null) {
            ruleSetRef = ",\"ruleSetId\":\"" + createRuleSet(federationId, win, draw, loss) + "\"";
        }
        return buildLeagueWithHomeWin(federationId, ruleSetRef);
    }

    /**
     * Build a league under {@code federationId} (with an optional {@code leagueRuleSetRef} JSON
     * fragment attaching a rule set to the league), play one home-win match day, confirm it.
     */
    private Scenario buildLeagueWithHomeWin(String federationId, String leagueRuleSetRef) throws Exception {
        String seasonId = create("/v1/seasons", "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        String categoryId = create("/v1/category", "{\"name\":\"Herren\",\"shortName\":\"H\"}");
        String leagueId = create("/v1/leagues", "{\"name\":\"Liga\",\"seasonId\":\"" + seasonId
            + "\",\"categoryId\":\"" + categoryId + "\"" + leagueRuleSetRef + "}");
        String tierId = create("/v1/tiers", "{\"name\":\"1. Liga\",\"leagueId\":\"" + leagueId + "\"}");
        String groupId = create("/v1/groups",
            "{\"name\":\"Gruppe A\",\"tierId\":\"" + tierId + "\",\"groupState\":\"RUNNING\"}");
        String roundId = create("/v1/rounds", "{\"name\":\"Runde1\",\"index\":1,\"groupId\":\"" + groupId + "\"}");
        String locationId = create("/v1/locations", "{\"name\":\"Halle\",\"address\":\"Musterstr 1\"}");

        String homeTeamId = create("/v1/teams", "{\"name\":\"Heim\",\"clubId\":\"" + createClub() + "\"}");
        String awayTeamId = create("/v1/teams", "{\"name\":\"Gast\",\"clubId\":\"" + createClub() + "\"}");
        String matchDayId = create("/v1/matchdays", String.format(
            "{\"name\":\"Spieltag\",\"roundId\":\"%s\",\"locationId\":\"%s\",\"teamHomeId\":\"%s\","
                + "\"teamAwayId\":\"%s\",\"startDate\":\"2025-01-01T00:00:00Z\"}",
            roundId, locationId, homeTeamId, awayTeamId));
        String matchId = create("/v1/matches", "{\"matchDayId\":\"" + matchDayId + "\","
            + "\"startTime\":\"2025-01-01T10:00:00Z\",\"endTime\":\"2025-01-01T10:30:00Z\","
            + "\"type\":\"SINGLE\"}");

        // Home team's admin submits a home win (5:2); the away team's admin confirms it.
        RequestPostProcessor home = teamAdmin("home", homeTeamId);
        RequestPostProcessor away = teamAdmin("away", awayTeamId);
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/result").with(home)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"matches\":[{\"matchId\":\"" + matchId + "\",\"homeScore\":5,\"awayScore\":2}]}"))
            .andExpect(status().isOk());
        mockMvc.perform(post("/v1/matchdays/" + matchDayId + "/confirm").with(away))
            .andExpect(status().isOk());

        return new Scenario(groupId, homeTeamId, awayTeamId);
    }

    // --- reads ---

    private int pointsOf(String groupId, String teamId) throws Exception {
        return standingField(groupId, teamId, "points");
    }

    private int winsOf(String groupId, String teamId) throws Exception {
        return standingField(groupId, teamId, "wins");
    }

    private int lossesOf(String groupId, String teamId) throws Exception {
        return standingField(groupId, teamId, "losses");
    }

    private int standingField(String groupId, String teamId, String field) throws Exception {
        String json = mockMvc.perform(get("/v1/groups/" + groupId + "/standings"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<Integer> values = JsonPath.read(json, "$[?(@.teamId == '" + teamId + "')]." + field);
        assertThat(values).as("standing for team %s", teamId).hasSize(1);
        return values.get(0);
    }

    // --- helpers ---

    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }

    private void update(String path, String body) throws Exception {
        mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    private String createRuleSet(String federationId, int win, int draw, int loss) throws Exception {
        return create("/v1/league-rule-sets", String.format(
            "{\"name\":\"RS\",\"federationId\":\"%s\",\"playSystem\":\"ROUND_ROBIN\","
                + "\"pointsWin\":%d,\"pointsDraw\":%d,\"pointsLoss\":%d}", federationId, win, draw, loss));
    }

    /**
     * Seed (or reuse) a player with a TEAM_ADMIN grant on {@code teamId} and return its JWT
     * post-processor. {@code dtfb_id} is unique, and the DB is shared across test methods, so the
     * player is upserted rather than blindly inserted.
     */
    private RequestPostProcessor teamAdmin(String dtfbId, String teamId) {
        Player player = playerRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            Player p = new Player();
            p.setDtfbId(dtfbId);
            return playerRepository.save(p);
        });
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(player);
        grant.setRole(Role.TEAM_ADMIN);
        grant.setScopeType(ScopeType.TEAM);
        grant.setScopeId(teamId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private record Scenario(String groupId, String homeTeamId, String awayTeamId) {
    }
}
