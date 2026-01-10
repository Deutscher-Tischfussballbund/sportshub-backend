package de.dtfb.sportshub.backend.matchevent;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class MatchEventControllerTest {

    @Autowired
    MockMvc mockMvc;

    private String url;
    private String matchId;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
    private String teamHomeId;
    private final UUID playerId = UUID.randomUUID();

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonId = JsonPath.read(season.getResponse().getContentAsString(), "$.id");
        MvcResult event = createEvent(seasonId);
        String eventId = JsonPath.read(event.getResponse().getContentAsString(), "$.id");
        MvcResult discipline = createDiscipline(eventId);
        String disciplineId = JsonPath.read(discipline.getResponse().getContentAsString(), "$.id");
        MvcResult stage = createStage(disciplineId);
        String stageId = JsonPath.read(stage.getResponse().getContentAsString(), "$.id");
        MvcResult pool = createPool(stageId);
        String poolId = JsonPath.read(pool.getResponse().getContentAsString(), "$.id");
        MvcResult round = createRound(poolId);
        String roundId = JsonPath.read(round.getResponse().getContentAsString(), "$.id");

        MvcResult location = createLocation();
        String locationId = JsonPath.read(location.getResponse().getContentAsString(), "$.id");
        MvcResult teamHome = createTeam("Hand und Foos");
        teamHomeId = JsonPath.read(teamHome.getResponse().getContentAsString(), "$.id");
        MvcResult teamAway = createTeam("Foos Fighters");
        String teamAwayId = JsonPath.read(teamAway.getResponse().getContentAsString(), "$.id");
        Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
        MvcResult matchDay = createMatchday(roundId, locationId, teamHomeId, teamAwayId, sampleDate);
        String matchDayId = JsonPath.read(matchDay.getResponse().getContentAsString(), "$.id");
        MvcResult match = createMatch(matchDayId, sampleDate);
        matchId = JsonPath.read(match.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult matchEvent = createMatchEvent();
        url = matchEvent.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatches() throws Exception {
        mockMvc.perform(get("/api/v1/matchevents"))
            .andExpect(status().isOk());
    }

    @Test
    void getMatch_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/matchevents/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatch() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("GOAL"))
            .andExpect(jsonPath("$.matchId").value(matchId))
            .andExpect(jsonPath("$.teamId").value(teamHomeId))
            .andExpect(jsonPath("$.playerId").value(playerId.toString()))
            .andExpect(jsonPath("$.json").value("{\"name\": \"test\"}"))
            .andExpect(jsonPath("$.homeScore").value(5))
            .andExpect(jsonPath("$.awayScore").value(4));
    }

    @Test
    void updateMatch() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "TIMEOUT",
                            "matchId": "%s",
                            "teamId": "%s"
                            }
                    """, matchId, teamHomeId)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("TIMEOUT"))
            .andExpect(jsonPath("$.matchId").value(matchId))
            .andExpect(jsonPath("$.teamId").value(teamHomeId))
            .andExpect(jsonPath("$.playerId").value(playerId.toString()))
            .andExpect(jsonPath("$.json").value("{\"name\": \"test\"}"))
            .andExpect(jsonPath("$.homeScore").value(5))
            .andExpect(jsonPath("$.awayScore").value(4))
            .andReturn().getResponse().getContentAsString();

        // Workaround due to hamcrest only doing string matches
        Instant timestamp = Instant.parse(JsonPath.read(json, "$.timestamp"));
        Assertions.assertThat(timestamp).isEqualTo(sampleDate);
    }

    @Test
    void updateMatch_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/matchevents/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"type": "START"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatch() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatch_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/matchevents/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private MvcResult createSeason() throws Exception {
        return mockMvc.perform(post("/api/v1/seasons")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"name": "2025"}
                """)).andReturn();
    }

    private MvcResult createEvent(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Turnier",
                            "seasonId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createDiscipline(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Offenes Einzel",
                            "eventId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createStage(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Vorrunde",
                            "disciplineId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createPool(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/pools")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Pool1",
                            "tournamentMode": "SWISS",
                            "stageId": "%s",
                            "poolState": "READY"
                            }
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createRound(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/rounds")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Runde1",
                            "index": 1,
                            "poolId": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createLocation() throws Exception {
        return mockMvc.perform(post("/api/v1/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "MKK",
                            "address": "Flensburg, Musterstraße 1"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createTeam(String name) throws Exception {
        return mockMvc.perform(post("/api/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "%s"}
                    """, name)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createMatchday(String roundId, String locationId, String teamHomeId, String teamAwayId, Instant sampleDate) throws Exception {
        return mockMvc.perform(post("/api/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Matchday1",
                            "roundId": "%s",
                            "locationId": "%s",
                            "teamAwayId": "%s",
                            "teamHomeId": "%s",
                            "startDate": "%s",
                            "endDate": "%s"
                            }
                    """, roundId, locationId, teamAwayId, teamHomeId, sampleDate, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createMatch(String uuid, Instant sampleDate) throws Exception {
        return mockMvc.perform(post("/api/v1/matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "DOUBLE",
                            "state": "PLAYED",
                            "matchDayId": "%s",
                            "homeScore": 10,
                            "awayScore": 6,
                            "startTime": "%s",
                            "endTime": "%s"
                            }
                    """, uuid, sampleDate, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createMatchEvent() throws Exception {
        return mockMvc.perform(post("/api/v1/matchevents")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "GOAL",
                            "matchId": "%s",
                            "teamId": "%s",
                            "playerId": "%s",
                            "homeScore": 5,
                            "awayScore": 4,
                            "timestamp": "%s",
                            "json": {"name": "test"}
                            }
                    """, matchId, teamHomeId, playerId, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
