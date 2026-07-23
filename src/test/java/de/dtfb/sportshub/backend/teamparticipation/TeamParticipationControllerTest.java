package de.dtfb.sportshub.backend.teamparticipation;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.standing.Standing;
import de.dtfb.sportshub.backend.standing.StandingRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TeamParticipationControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    @Autowired
    private StandingRepository standingRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private TeamRepository teamRepository;

    private String seasonId;
    private String leagueId;
    private String groupId;
    private String teamId;
    private String url;

    @PostConstruct
    void setup() throws Exception {
        seasonId = id(createSeason());
        leagueId = id(createLeague(seasonId));
        String tierId = id(createTier(leagueId));
        groupId = id(createGroup(tierId));
        teamId = id(createTeam("Hand und Foos"));
    }

    @BeforeEach
    void setupEach() throws Exception {
        // Register a team without a division yet (registered-but-unplaced).
        url = createParticipation(null).getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllParticipations() throws Exception {
        mockMvc.perform(get("/v1/team-participations")).andExpect(status().isOk());
    }

    @Test
    void getAllParticipations_filteredByTeam() throws Exception {
        mockMvc.perform(get("/v1/team-participations").param("teamId", teamId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].teamId").value(teamId));

        mockMvc.perform(get("/v1/team-participations").param("teamId", NanoIdUtils.randomNanoId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
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
            .andExpect(jsonPath("$.leagueId").value(leagueId))
            // season is derived from the league, not sent by the client
            .andExpect(jsonPath("$.seasonId").value(seasonId));
    }

    @Test
    void updateParticipation_placesIntoGroup() throws Exception {
        // promote/relegate: move the team into a division
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": "%s"}
                    """, teamId, leagueId, groupId)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").value(groupId));
    }

    @Test
    void updateParticipation_expectException() throws Exception {
        mockMvc.perform(put("/v1/team-participations/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, leagueId)))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation_expectException() throws Exception {
        mockMvc.perform(delete("/v1/team-participations/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteParticipation_blockedWhenMatchDayExists() throws Exception {
        String opponentId = id(createTeam("Gegner"));
        String roundId = id(createRound(groupId));
        createMatchDay(roundId, teamId, opponentId);

        mockMvc.perform(delete(url))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PARTICIPATION_HAS_MATCHES"));

        // untouched -- still there, still deletable once the block is lifted (not exercised here)
        mockMvc.perform(get(url)).andExpect(status().isOk());
    }

    @Test
    void deleteParticipation_blockedWhenStandingExists() throws Exception {
        Standing standing = new Standing();
        standing.setGroup(groupRepository.findById(groupId).orElseThrow());
        standing.setTeam(teamRepository.findById(teamId).orElseThrow());
        standingRepository.save(standing);

        mockMvc.perform(delete(url))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("PARTICIPATION_HAS_MATCHES"));
    }

    @Test
    void withdraw_setsStatusAndTimestamp() throws Exception {
        mockMvc.perform(post(url + "/withdraw"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"))
            .andExpect(jsonPath("$.withdrawnAt").isNotEmpty());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("WITHDRAWN"));
    }

    @Test
    void withdraw_alreadyWithdrawn_isConflict() throws Exception {
        mockMvc.perform(post(url + "/withdraw")).andExpect(status().isOk());
        mockMvc.perform(post(url + "/withdraw")).andExpect(status().isConflict());
    }

    @Test
    void withdraw_expectException() throws Exception {
        mockMvc.perform(post("/v1/team-participations/" + NanoIdUtils.randomNanoId() + "/withdraw"))
            .andExpect(status().isNotFound());
    }

    @Test
    void withdrawnParticipation_canStillBeDeleted_ifNoMatches() throws Exception {
        // Withdrawing alone doesn't block delete -- only recorded matches/standings do.
        mockMvc.perform(post(url + "/withdraw")).andExpect(status().isOk());
        mockMvc.perform(delete(url)).andExpect(status().isOk());
    }

    @Test
    void createParticipation_forEndedSeason_isConflict() throws Exception {
        String endedLeagueId = createEndedLeague();

        mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, endedLeagueId)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEASON_ENDED"))
            .andExpect(jsonPath("$.endDate").value("2020-05-31"));
    }

    @Test
    void updateParticipation_forEndedSeason_isAllowed() throws Exception {
        // Placement edits (e.g. moving an existing participation) stay unrestricted regardless
        // of season timing — only fresh registrations (create) are blocked.
        String endedLeagueId = createEndedLeague();

        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s"}
                    """, teamId, endedLeagueId)))
            .andExpect(status().isOk());
    }

    //region helpers
    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** A league under a season that ended 2020-05-31 (for the season-ended registration tests). */
    private String createEndedLeague() throws Exception {
        String federationId = createFederation();
        MvcResult endedSeason = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "2019", "federationId": "%s", "startDate": "2019-09-01", "endDate": "2020-05-31"}
                    """, federationId)))
            .andExpect(status().isCreated()).andReturn();
        return id(createLeague(id(endedSeason)));
    }

    private MvcResult createSeason() throws Exception {
        String federationId = createFederation();
        return mockMvc.perform(post("/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content(String.format("""
                {"name": "2025", "federationId": "%s"}
                """, federationId))).andReturn();
    }

    private MvcResult createLeague(String seasonId) throws Exception {
        String categoryId = createCategory();
        return mockMvc.perform(post("/v1/leagues")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Bayernliga", "seasonId": "%s", "categoryId": "%s"}
                    """, seasonId, categoryId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createTier(String leagueId) throws Exception {
        return mockMvc.perform(post("/v1/tiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bayernliga", "leagueId": "%s"}
                    """, leagueId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createGroup(String tierId) throws Exception {
        return mockMvc.perform(post("/v1/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "1. Bundesliga", "tierId": "%s", "groupState": "PLANNED"}
                    """, tierId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createTeam(String name) throws Exception {
        String clubId = createClub();
        return mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s", "clubId": "%s"}
                    """, name, clubId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createRound(String groupId) throws Exception {
        return mockMvc.perform(post("/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde 1", "groupId": "%s"}
                    """, groupId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createMatchDay(String roundId, String teamHomeId, String teamAwayId) throws Exception {
        String locationId = id(createLocation());
        return mockMvc.perform(post("/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Spieltag 1", "roundId": "%s", "teamHomeId": "%s", "teamAwayId": "%s",
                             "locationId": "%s", "startDate": "%s"}
                    """, roundId, teamHomeId, teamAwayId, locationId, Instant.parse("2025-01-01T10:00:00Z"))))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createLocation() throws Exception {
        String federationId = createFederation();
        return mockMvc.perform(post("/v1/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Halle 1", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createParticipation(String groupId) throws Exception {
        String group = groupId == null ? "null" : "\"" + groupId + "\"";
        return mockMvc.perform(post("/v1/team-participations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"teamId": "%s", "leagueId": "%s", "groupId": %s}
                    """, teamId, leagueId, group)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
