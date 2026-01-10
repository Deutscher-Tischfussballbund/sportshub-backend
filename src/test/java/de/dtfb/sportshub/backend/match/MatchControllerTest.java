package de.dtfb.sportshub.backend.match;

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
class MatchControllerTest {

    @Autowired
    MockMvc mockMvc;

    private String url;
    private String uuid;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision

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
        String teamHomeId = JsonPath.read(teamHome.getResponse().getContentAsString(), "$.id");
        MvcResult teamAway = createTeam("Foos Fighters");
        String teamAwayId = JsonPath.read(teamAway.getResponse().getContentAsString(), "$.id");
        Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
        MvcResult matchDay = createMatchday(roundId, locationId, teamHomeId, teamAwayId, sampleDate);
        uuid = JsonPath.read(matchDay.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult match = createMatch();
        url = match.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatches() throws Exception {
        mockMvc.perform(get("/api/v1/matches"))
            .andExpect(status().isOk());
    }

    @Test
    void getMatch_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/matches/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatch() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("DOUBLE"))
            .andExpect(jsonPath("$.state").value("PLAYED"))
            .andExpect(jsonPath("$.matchDayId").value(uuid))
            .andExpect(jsonPath("$.homeScore").value(10))
            .andExpect(jsonPath("$.awayScore").value(6));
    }

    @Test
    void updateMatch() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "SINGLE",
                            "matchDayId": "%s"
                            }
                    """, uuid)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("SINGLE"))
            .andExpect(jsonPath("$.state").value("PLAYED"))
            .andExpect(jsonPath("$.matchDayId").value(uuid))
            .andExpect(jsonPath("$.homeScore").value(10))
            .andExpect(jsonPath("$.awayScore").value(6))
            .andReturn().getResponse().getContentAsString();

        // Workaround due to hamcrest only doing string matches
        Instant start = Instant.parse(JsonPath.read(json, "$.startTime"));
        Instant end = Instant.parse(JsonPath.read(json, "$.endTime"));
        Assertions.assertThat(start).isEqualTo(sampleDate);
        Assertions.assertThat(end).isEqualTo(sampleDate);
    }

    @Test
    void updateMatch_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/matches/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"state": "PLANNED"}
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
        mockMvc.perform(delete("/api/v1/matches/" + UUID.randomUUID()))
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

    private MvcResult createMatch() throws Exception {
        return mockMvc.perform(post("/api/v1/matches")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"type": "DOUBLE",
                            "state": "PLAYED",
                            "matchDayId": "%s",
                            "homeScore": "10",
                            "awayScore": "6",
                            "startTime": "%s",
                            "endTime": "%s"
                            }
                    """, uuid, sampleDate, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
