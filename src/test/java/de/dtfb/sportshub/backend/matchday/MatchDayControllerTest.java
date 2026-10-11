package de.dtfb.sportshub.backend.matchday;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.assertj.core.api.Assertions;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class MatchDayControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    private String url;
    private String roundId;
    private String groupId;
    private String leagueId;
    private String seasonId;
    private String locationId;
    private String teamHomeId;
    private String teamAwayId;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision

    @PostConstruct
    void setup() throws Exception {
        seasonId = id(createSeason());
        leagueId = id(createLeague(seasonId));
        String tierId = id(createTier(leagueId));
        groupId = id(createGroup(tierId));
        roundId = id(createRound(groupId));

        locationId = id(createLocation());
        teamHomeId = id(createTeam("Hand und Foos"));
        teamAwayId = id(createTeam("Foos Fighters"));
    }

    @BeforeEach
    void setupEach() throws Exception {
        url = createMatchday().getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatchdays_withoutAScope_isRefused() throws Exception {
        mockMvc.perform(get("/v1/matchdays")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("FILTER_REQUIRED"));
        // from/to/resultState alone don't scope
        mockMvc.perform(get("/v1/matchdays").param("from", "2026-01-01").param("resultState", "OPEN"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getAllMatchdays_filtersCombineWithAnd() throws Exception {
        String id = url.substring(url.lastIndexOf('/') + 1);
        LocalDate today = LocalDate.ofInstant(sampleDate, ZoneId.of("Europe/Berlin"));

        for (String[] scope : new String[][] {{"groupId", groupId}, {"roundId", roundId}, {"leagueId", leagueId},
            {"seasonId", seasonId}, {"teamId", teamHomeId}, {"teamId", teamAwayId}}) {
            mockMvc.perform(get("/v1/matchdays").param(scope[0], scope[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id").value(Matchers.contains(id)));
        }
        mockMvc.perform(get("/v1/matchdays").param("teamId", NanoIdUtils.randomNanoId()))
            .andExpect(jsonPath("$").isEmpty());
        String identity = JsonPath.read(mockMvc.perform(get("/v1/teams/" + teamAwayId))
            .andReturn().getResponse().getContentAsString(), "$.teamIdentityId");
        mockMvc.perform(get("/v1/matchdays").param("teamId", identity))
            .andExpect(jsonPath("$[*].id").value(Matchers.contains(id)));
        // dates are inclusive calendar days
        mockMvc.perform(get("/v1/matchdays").param("groupId", groupId)
                .param("from", today.toString()).param("to", today.toString()))
            .andExpect(jsonPath("$[*].id").value(Matchers.contains(id)));
        mockMvc.perform(get("/v1/matchdays").param("groupId", groupId).param("from", today.plusDays(1).toString()))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/v1/matchdays").param("groupId", groupId).param("to", today.minusDays(1).toString()))
            .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(get("/v1/matchdays").param("groupId", groupId).param("resultState", "CONFIRMED"))
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void getMatchday_expectException() throws Exception {
        mockMvc.perform(get("/v1/matchdays/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatchday() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Matchday1"))
            .andExpect(jsonPath("$.locationId").value(locationId))
            .andExpect(jsonPath("$.roundId").value(roundId))
            .andExpect(jsonPath("$.teamAwayId").value(teamAwayId))
            .andExpect(jsonPath("$.teamHomeId").value(teamHomeId))
            .andExpect(jsonPath("$.teamHomeIdentityId").isNotEmpty())
            .andExpect(jsonPath("$.teamAwayIdentityId").isNotEmpty())
            .andExpect(jsonPath("$.teamHomeName").isNotEmpty())
            .andExpect(jsonPath("$.teamAwayName").isNotEmpty());
    }

    @Test
    void updateMatchday() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "AnotherMatchday", "roundId": "%s", "locationId": "%s",
                            "teamAwayId": "%s", "teamHomeId": "%s"}
                    """, roundId, locationId, teamAwayId, teamHomeId)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("AnotherMatchday"))
            .andReturn().getResponse().getContentAsString();

        Instant start = Instant.parse(JsonPath.read(json, "$.startDate"));
        Instant end = Instant.parse(JsonPath.read(json, "$.endDate"));
        Assertions.assertThat(start).isEqualTo(sampleDate);
        Assertions.assertThat(end).isEqualTo(sampleDate);
    }

    @Test
    void updateMatchday_expectException() throws Exception {
        mockMvc.perform(put("/v1/matchdays/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Matchday1"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchday() throws Exception {
        mockMvc.perform(delete(url)).andExpect(status().isOk());
        mockMvc.perform(get(url)).andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchday_expectException() throws Exception {
        mockMvc.perform(delete("/v1/matchdays/" + NanoIdUtils.randomNanoId()))
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
        String federationId = createFederation();
        String clubId = createClub(federationId);
        String seasonId = createSeasonId(federationId);
        return mockMvc.perform(post("/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s", "clubId": "%s", "seasonId": "%s"}
                    """, name, clubId, seasonId)))
            .andExpect(status().isCreated()).andReturn();
    }

    private MvcResult createMatchday() throws Exception {
        return mockMvc.perform(post("/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Matchday1", "roundId": "%s", "locationId": "%s",
                            "teamAwayId": "%s", "teamHomeId": "%s", "startDate": "%s", "endDate": "%s"}
                    """, roundId, locationId, teamAwayId, teamHomeId, sampleDate, sampleDate)))
            .andExpect(status().isCreated()).andReturn();
    }
    //endregion
}
