package de.dtfb.sportshub.backend.club;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Club is a single, un-duplicated row (see {@code docs/14-team-player-versioning.md}) -- editing
 * it records a change-history entry per changed field instead of cloning the row. A team that
 * already existed under the club's old name must keep showing that old name for its own season
 * (see {@link de.dtfb.sportshub.backend.team.TeamDto#getClubName()}).
 */
class ClubControllerTest extends AuthorizedControllerTest {

    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void clubs_listsSeededClubs() throws Exception {
        mockMvc.perform(get("/v1/admin/clubs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void update_rewritesTrackedFields_andRecordsHistory() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);

        mockMvc.perform(put("/v1/admin/clubs/" + clubId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Neuer Name", "shortName": "NN", "city": "Berlin", "active": true, "regionId": "%s"}
                    """, federationId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Neuer Name"));

        mockMvc.perform(get("/v1/admin/clubs/" + clubId + "/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.fieldName == 'name')].oldValue").value("Testverein"))
            .andExpect(jsonPath("$[?(@.fieldName == 'name')].newValue").value("Neuer Name"));
    }

    @Test
    void create_addsAClub() throws Exception {
        String federationId = createFederation();

        MvcResult result = mockMvc.perform(post("/v1/admin/clubs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Neuer Verein", "shortName": "NV", "city": "Hamburg", "active": true, "regionId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Neuer Verein"))
            .andExpect(jsonPath("$.regionId").value(federationId))
            .andReturn();
        String clubId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/v1/admin/clubs"))
            .andExpect(jsonPath("$[?(@.id == '" + clubId + "')].name").value("Neuer Verein"));
    }

    @Test
    void create_unknownRegion_isNotFound() throws Exception {
        mockMvc.perform(post("/v1/admin/clubs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "X", "active": true, "regionId": "does-not-exist"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_removesAnUnusedClub() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);

        mockMvc.perform(delete("/v1/admin/clubs/" + clubId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/admin/clubs"))
            .andExpect(jsonPath("$[?(@.id == '" + clubId + "')]").isEmpty());
    }

    @Test
    void delete_unknownClub_isNotFound() throws Exception {
        mockMvc.perform(delete("/v1/admin/clubs/does-not-exist"))
            .andExpect(status().isNotFound());
    }

    @Test
    void delete_clubWithTeam_isConflict() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);
        String seasonId = createSeason(federationId, "2020-01-01", null);
        mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testteam", "clubId": "%s", "seasonId": "%s"}
                    """, clubId, seasonId)))
            .andExpect(status().isCreated());

        mockMvc.perform(delete("/v1/admin/clubs/" + clubId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLUB_HAS_TEAMS_OR_MEMBERS"));
    }

    @Test
    void delete_clubWithActiveMember_isConflict() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);
        Player player = new Player();
        player.setFirstName("Test");
        player.setLastName("Spieler");
        String playerId = playerRepository.save(player).getId();
        joinClub(playerId, clubId);

        mockMvc.perform(delete("/v1/admin/clubs/" + clubId))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CLUB_HAS_TEAMS_OR_MEMBERS"));
    }

    @Test
    void update_unknownClub_isNotFound() throws Exception {
        mockMvc.perform(put("/v1/admin/clubs/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "X", "active": true}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void clubRename_doesNotRewriteAnEndedSeasonsTeamsHistoricalDisplay() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);
        String seasonId = createSeason(federationId, "2020-01-01", "2020-12-31");

        MvcResult teamResult = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testteam", "clubId": "%s", "seasonId": "%s"}
                    """, clubId, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        String teamId = JsonPath.read(teamResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/v1/teams/" + teamId))
            .andExpect(jsonPath("$.clubName").value("Testverein"));

        mockMvc.perform(put("/v1/admin/clubs/" + clubId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Umbenannter Verein", "active": true, "regionId": "%s"}
                    """, federationId)))
            .andExpect(status().isOk());

        // the team's own display still shows the club's name as of the team's (ended) season, not today's
        mockMvc.perform(get("/v1/teams/" + teamId))
            .andExpect(jsonPath("$.clubName").value("Testverein"));

        // but the club itself, read directly, shows its current name
        mockMvc.perform(get("/v1/admin/clubs"))
            .andExpect(jsonPath("$[?(@.id == '" + clubId + "')].name").value("Umbenannter Verein"));
    }

    @Test
    void clubRename_updatesACurrentSeasonsTeamsDisplay() throws Exception {
        String federationId = createFederation();
        String clubId = createClub(federationId);
        String seasonId = createSeason(federationId, "2020-01-01", null);

        MvcResult teamResult = mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Testteam", "clubId": "%s", "seasonId": "%s"}
                    """, clubId, seasonId)))
            .andExpect(status().isCreated())
            .andReturn();
        String teamId = JsonPath.read(teamResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/v1/admin/clubs/" + clubId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Umbenannter Verein", "active": true, "regionId": "%s"}
                    """, federationId)))
            .andExpect(status().isOk());

        // the season is still open, so the team must reflect the rename, not freeze it
        mockMvc.perform(get("/v1/teams/" + teamId))
            .andExpect(jsonPath("$.clubName").value("Umbenannter Verein"));
    }

    private String createSeason(String federationId, String startDate, String endDate) throws Exception {
        // A real startDate is required: an ENDED season's TeamDto.clubName is reconstructed as of
        // the team's own season start (see TeamService#toDtoAsOf) -- a null startDate would fall
        // back to the club's current name and clubRename_doesNotRewrite... would not actually
        // exercise the point-in-time path.
        String endDateJson = endDate == null ? "" : String.format(", \"endDate\": \"%s\"", endDate);
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "S", "federationId": "%s", "startDate": "%s", "registrationOpensAt": "%s"%s}
                    """, federationId, startDate, startDate, endDateJson)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
}
