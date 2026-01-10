package de.dtfb.sportshub.backend.matchset;

import com.jayway.jsonpath.JsonPath;
import jakarta.annotation.PostConstruct;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class MatchSetControllerTest {

    @Autowired
    MockMvc mockMvc;

    private String url;
    private String uuid;

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
        String matchDayId = JsonPath.read(matchDay.getResponse().getContentAsString(), "$.id");
        MvcResult match = createMatch(matchDayId, sampleDate);
        uuid = JsonPath.read(match.getResponse().getContentAsString(), "$.id");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult matchSet = createMatchset();
        url = matchSet.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatchsets() throws Exception {
        mockMvc.perform(get("/api/v1/matchsets"))
            .andExpect(status().isOk());
    }

    @Test
    void getMatchset_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/matchsets/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatchset() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setNumber").value(1))
            .andExpect(jsonPath("$.matchId").value(uuid))
            .andExpect(jsonPath("$.homeScore").value(5))
            .andExpect(jsonPath("$.awayScore").value(2));
    }

    @Test
    void updateMatchset() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"awayScore": "4",
                            "matchId": "%s"
                            }
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setNumber").value(1))
            .andExpect(jsonPath("$.matchId").value(uuid))
            .andExpect(jsonPath("$.homeScore").value(5))
            .andExpect(jsonPath("$.awayScore").value(4));
    }

    @Test
    void updateMatchset_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/matchsets/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"homeScore": "2"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchset() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchset_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/matchsets/" + UUID.randomUUID()))
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
                            "homeScore": "10",
                            "awayScore": "6",
                            "startTime": "%s",
                            "endTime": "%s"
                            }
                    """, uuid, sampleDate, sampleDate)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private MvcResult createMatchset() throws Exception {
        return mockMvc.perform(post("/api/v1/matchsets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"setNumber": "1",
                            "matchId": "%s",
                            "homeScore": "5",
                            "awayScore": "2"
                            }
                    """, uuid)))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
