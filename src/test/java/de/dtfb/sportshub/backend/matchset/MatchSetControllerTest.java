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
        String teamHomeUuid = JsonPath.read(teamHome.getResponse().getContentAsString(), "$.uuid");
        MvcResult teamAway = createTeam("Foos Fighters");
        String teamAwayUuid = JsonPath.read(teamAway.getResponse().getContentAsString(), "$.uuid");
        Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision
        MvcResult matchDay = createMatchday(roundUuid, locationUuid, teamHomeUuid, teamAwayUuid, sampleDate);
        String matchDayUuid = JsonPath.read(matchDay.getResponse().getContentAsString(), "$.uuid");
        MvcResult match = createMatch(matchDayUuid, sampleDate);
        uuid = JsonPath.read(match.getResponse().getContentAsString(), "$.uuid");
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
            .andExpect(jsonPath("$.matchUuid").value(uuid))
            .andExpect(jsonPath("$.homeScore").value(5))
            .andExpect(jsonPath("$.awayScore").value(2));
    }

    @Test
    void updateMatchset() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"awayScore": "4",
                            "matchUuid": "%s"
                            }
                    """, uuid)))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.setNumber").value(1))
            .andExpect(jsonPath("$.matchUuid").value(uuid))
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
                            "matchUuid": "%s",
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
