package de.dtfb.sportshub.backend.match;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class MatchControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String url;
    private String matchDayId;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision

    @PostConstruct
    void setup() throws Exception {
        String seasonId = id(createSeason());
        String leagueId = id(createLeague(seasonId));
        String tierId = id(createTier(leagueId));
        String groupId = id(createGroup(tierId));
        String roundId = id(createRound(groupId));
        String locationId = id(createLocation());
        String teamHomeId = id(createTeam("Hand und Foos"));
        String teamAwayId = id(createTeam("Foos Fighters"));
        matchDayId = id(createMatchday(roundId, locationId, teamHomeId, teamAwayId));
    }

    @BeforeEach
    void setupEach() throws Exception {
        url = createMatch().getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatches() throws Exception {
        mockMvc.perform(get("/v1/matches")).andExpect(status().isOk());
    }

    @Test
    void getMatch_expectException() throws Exception {
        mockMvc.perform(get("/v1/matches/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatch() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("DOUBLE"))
            .andExpect(jsonPath("$.state").value("PLAYED"))
            .andExpect(jsonPath("$.matchDayId").value(matchDayId))
            .andExpect(jsonPath("$.homeScore").value(10))
            .andExpect(jsonPath("$.awayScore").value(6));
    }

    @Test
    void updateMatch() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "SINGLE", "matchDayId": "%s"}
                    """, matchDayId)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("SINGLE"))
            .andExpect(jsonPath("$.state").value("PLAYED"))
            .andExpect(jsonPath("$.matchDayId").value(matchDayId))
            .andExpect(jsonPath("$.homeScore").value(10))
            .andExpect(jsonPath("$.awayScore").value(6))
            .andReturn().getResponse().getContentAsString();

        Instant start = Instant.parse(JsonPath.read(json, "$.startTime"));
        Instant end = Instant.parse(JsonPath.read(json, "$.endTime"));
        Assertions.assertThat(start).isEqualTo(sampleDate);
        Assertions.assertThat(end).isEqualTo(sampleDate);
    }

    @Test
    void updateMatch_expectException() throws Exception {
        mockMvc.perform(put("/v1/matches/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"state": "PLANNED"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatch() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteMatch_expectException() throws Exception {
        mockMvc.perform(delete("/v1/matches/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    //region helpers
    private String id(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
                            {"name": "Gruppe A", "tierId": "%s", "groupState": "READY"}
                    """, tierId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createRound(String groupId) throws Exception {
        return mockMvc.perform(post("/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde1", "index": 1, "groupId": "%s"}
                    """, groupId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createLocation() throws Exception {
        return mockMvc.perform(post("/v1/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "MKK", "address": "Flensburg, Musterstraße 1"}
                    """))
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

    private MvcResult createMatchday(String roundId, String locationId, String teamHomeId, String teamAwayId)
        throws Exception {
        return mockMvc.perform(post("/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Matchday1", "roundId": "%s", "locationId": "%s",
                            "teamAwayId": "%s", "teamHomeId": "%s", "startDate": "%s", "endDate": "%s"}
                    """, roundId, locationId, teamAwayId, teamHomeId, sampleDate, sampleDate)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createMatch() throws Exception {
        return mockMvc.perform(post("/v1/matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "DOUBLE", "state": "PLAYED", "matchDayId": "%s",
                            "homeScore": 10, "awayScore": 6, "startTime": "%s", "endTime": "%s"}
                    """, matchDayId, sampleDate, sampleDate)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
