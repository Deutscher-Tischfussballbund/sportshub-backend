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
    private String matchUuid;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
    private String teamHomeUuid;
    private final UUID playerUuid = UUID.randomUUID();

    @PostConstruct
    void setup() throws Exception {
        MvcResult season = createSeason();
        String seasonUuid = JsonPath.read(season.getResponse().getContentAsString(), "$.uuid");
        MvcResult event = createEvent(seasonUuid);
        String eventUuid = JsonPath.read(event.getResponse().getContentAsString(), "$.uuid");
        MvcResult discipline = createDiscipline(eventUuid);
        String disciplineUuid = JsonPath.read(discipline.getResponse().getContentAsString(), "$.uuid");
        MvcResult stage = createStage(disciplineUuid);
        String stageUuid = JsonPath.read(stage.getResponse().getContentAsString(), "$.uuid");
        MvcResult pool = createPool(stageUuid);
        String poolUuid = JsonPath.read(pool.getResponse().getContentAsString(), "$.uuid");
        MvcResult round = createRound(poolUuid);
        String roundUuid = JsonPath.read(round.getResponse().getContentAsString(), "$.uuid");

        MvcResult location = createLocation();
        String locationUuid = JsonPath.read(location.getResponse().getContentAsString(), "$.uuid");
        MvcResult teamHome = createTeam("Hand und Foos");
        teamHomeUuid = JsonPath.read(teamHome.getResponse().getContentAsString(), "$.uuid");
        MvcResult teamAway = createTeam("Foos Fighters");
        String teamAwayUuid = JsonPath.read(teamAway.getResponse().getContentAsString(), "$.uuid");
        Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
        MvcResult matchDay = createMatchday(roundUuid, locationUuid, teamHomeUuid, teamAwayUuid, sampleDate);
        String matchDayUuid = JsonPath.read(matchDay.getResponse().getContentAsString(), "$.uuid");
        MvcResult match = createMatch(matchDayUuid, sampleDate);
        matchUuid = JsonPath.read(match.getResponse().getContentAsString(), "$.uuid");
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
            .andExpect(jsonPath("$.matchUuid").value(matchUuid))
            .andExpect(jsonPath("$.teamUuid").value(teamHomeUuid))
            .andExpect(jsonPath("$.playerUuid").value(playerUuid.toString()))
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
                            "matchUuid": "%s",
                            "teamUuid": "%s"
                            }
                    """, matchUuid, teamHomeUuid)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("TIMEOUT"))
            .andExpect(jsonPath("$.matchUuid").value(matchUuid))
            .andExpect(jsonPath("$.teamUuid").value(teamHomeUuid))
            .andExpect(jsonPath("$.playerUuid").value(playerUuid.toString()))
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
                            "seasonUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createDiscipline(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/disciplines")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Offenes Einzel",
                            "eventUuid": "%s"}
                    """, uuid)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createStage(String uuid) throws Exception {
        return mockMvc.perform(post("/api/v1/stages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Vorrunde",
                            "disciplineUuid": "%s"}
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
                            "stageUuid": "%s",
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
                            "poolUuid": "%s"}
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

    private MvcResult createMatchday(String roundUuid, String locationUuid, String teamHomeUuid, String teamAwayUuid, Instant sampleDate) throws Exception {
        return mockMvc.perform(post("/api/v1/matchdays")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "Matchday1",
                            "roundUuid": "%s",
                            "locationUuid": "%s",
                            "teamAwayUuid": "%s",
                            "teamHomeUuid": "%s",
                            "startDate": "%s",
                            "endDate": "%s"
                            }
                    """, roundUuid, locationUuid, teamAwayUuid, teamHomeUuid, sampleDate, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createMatch(String uuid, Instant sampleDate) throws Exception {
        return mockMvc.perform(post("/api/v1/matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "DOUBLE",
                            "state": "PLAYED",
                            "matchDayUuid": "%s",
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
                            "matchUuid": "%s",
                            "teamUuid": "%s",
                            "playerUuid": "%s",
                            "homeScore": 5,
                            "awayScore": 4,
                            "timestamp": "%s",
                            "json": {"name": "test"}
                            }
                    """, matchUuid, teamHomeUuid, playerUuid, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
