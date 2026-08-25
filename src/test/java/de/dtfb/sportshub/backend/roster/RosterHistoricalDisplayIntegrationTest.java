package de.dtfb.sportshub.backend.roster;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Player is a single, un-duplicated row: a rename must not rewrite how a player already on a
 * roster displays there -- but only for a season that's over. {@link RosterService#getRoster}
 * reconstructs an ENDED season's roster with the name as it stood when the player was added to
 * that specific roster (see {@code EntityHistoryService#fieldsAsOf}); a current/future season's
 * roster is still live, so it must keep showing today's name instead.
 */
class RosterHistoricalDisplayIntegrationTest extends AuthorizedControllerTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void rosterEntry_ofEndedSeason_keepsShowingTheNameThePlayerHadWhenAdded() throws Exception {
        String federationId = createFederation();
        // Registering into an already-ended season is rejected (SeasonEndedException), so the
        // season starts open -- exactly like a real season does -- and is ended only afterward,
        // once the roster already exists, mirroring how a season actually plays out over time.
        String seasonId = createSeason(federationId, "2020-01-01", null);
        String leagueId = createLeague(seasonId);
        String clubId = createClub(federationId);
        String participationId = createParticipation(clubId, seasonId, leagueId);

        Player player = new Player();
        player.setFirstName("Lukas");
        player.setLastName("Bauer");
        String playerId = playerRepository.save(player).getId();
        joinClub(playerId, clubId);

        String rosterUrl = "/v1/team-participations/" + participationId + "/roster";
        mockMvc.perform(post(rosterUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"playerId\": \"%s\"}", playerId)))
            .andExpect(status().isCreated());

        endSeason(seasonId, federationId, "2020-01-01", "2020-12-31");

        mockMvc.perform(put("/v1/admin/players/" + playerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Lucas", "lastName": "Bauer", "active": true}
                    """))
            .andExpect(status().isOk());

        // the roster still shows the name as it was when the player was added, not today's name
        mockMvc.perform(get(rosterUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].firstName").value("Lukas"))
            .andExpect(jsonPath("$[0].lastName").value("Bauer"));

        // but the player, read directly, shows the current name
        mockMvc.perform(get("/v1/players/" + playerId))
            .andExpect(jsonPath("$.firstName").value("Lucas"));
    }

    @Test
    void rosterEntry_ofCurrentSeason_showsTodaysName() throws Exception {
        String federationId = createFederation();
        String seasonId = createSeason(federationId, "2020-01-01", null);
        String leagueId = createLeague(seasonId);
        String clubId = createClub(federationId);
        String participationId = createParticipation(clubId, seasonId, leagueId);

        Player player = new Player();
        player.setFirstName("Lukas");
        player.setLastName("Bauer");
        String playerId = playerRepository.save(player).getId();
        joinClub(playerId, clubId);

        String rosterUrl = "/v1/team-participations/" + participationId + "/roster";
        mockMvc.perform(post(rosterUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"playerId\": \"%s\"}", playerId)))
            .andExpect(status().isCreated());

        mockMvc.perform(put("/v1/admin/players/" + playerId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Lucas", "lastName": "Bauer", "active": true}
                    """))
            .andExpect(status().isOk());

        // the season is still open, so the roster must reflect the rename, not freeze it
        mockMvc.perform(get(rosterUrl))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].firstName").value("Lucas"))
            .andExpect(jsonPath("$[0].lastName").value("Bauer"));
    }

    private String createSeason(String federationId, String startDate, String endDate) throws Exception {
        String endDateJson = endDate == null ? "" : String.format(", \"endDate\": \"%s\"", endDate);
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "S", "federationId": "%s", "registrationOpensAt": "%s", "startDate": "%s"%s}
                    """, federationId, startDate, startDate, endDateJson)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private void endSeason(String seasonId, String federationId, String startDate, String endDate) throws Exception {
        mockMvc.perform(put("/v1/seasons/" + seasonId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "S", "federationId": "%s", "registrationOpensAt": "%s", "startDate": "%s", "endDate": "%s"}
                    """, federationId, startDate, startDate, endDate)))
            .andExpect(status().isOk());
    }

    private String createLeague(String seasonId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Liga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, createCategory())))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createParticipation(String clubId, String seasonId, String leagueId) throws Exception {
        MvcResult team = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\": \"Team\", \"clubId\": \"%s\", \"seasonId\": \"%s\"}",
                    clubId, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        String teamId = JsonPath.read(team.getResponse().getContentAsString(), "$.id");

        MvcResult participation = mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, leagueId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(participation.getResponse().getContentAsString(), "$.id");
    }
}
