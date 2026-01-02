package de.dtfb.sportshub.backend.matchday;

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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class MatchdayControllerTest {

    @Autowired
    MockMvc mockMvc;

    private String url;
    private String roundUuid;
    private String locationUuid;
    private String teamHomeUuid;
    private String teamAwayUuid;
    private final Instant sampleDate = Instant.now().truncatedTo(ChronoUnit.MICROS); // Database will lose precision

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
        roundUuid = JsonPath.read(round.getResponse().getContentAsString(), "$.uuid");

        MvcResult location = createLocation();
        locationUuid = JsonPath.read(location.getResponse().getContentAsString(), "$.uuid");
        MvcResult teamHome = createTeam("Hand und Foos");
        teamHomeUuid = JsonPath.read(teamHome.getResponse().getContentAsString(), "$.uuid");
        MvcResult teamAway = createTeam("Foos Fighters");
        teamAwayUuid = JsonPath.read(teamAway.getResponse().getContentAsString(), "$.uuid");
    }

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult matchday = createMatchday();
        url = matchday.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllMatchdays() throws Exception {
        mockMvc.perform(get("/api/v1/matchdays"))
            .andExpect(status().isOk());
    }

    @Test
    void getMatchday_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/matchdays/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetMatchday() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Matchday1"))
            .andExpect(jsonPath("$.locationUuid").value(locationUuid))
            .andExpect(jsonPath("$.roundUuid").value(roundUuid))
            .andExpect(jsonPath("$.teamAwayUuid").value(teamAwayUuid))
            .andExpect(jsonPath("$.teamHomeUuid").value(teamHomeUuid));
    }

    @Test
    void updateMatchday() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                            {"name": "AnotherMatchday",
                            "roundUuid": "%s",
                            "locationUuid": "%s",
                            "teamAwayUuid": "%s",
                            "teamHomeUuid": "%s"
                            }
                    """, roundUuid, locationUuid, teamAwayUuid, teamHomeUuid)))
            .andExpect(status().isOk());

        String json = mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("AnotherMatchday"))
            .andReturn().getResponse().getContentAsString();

        // Workaround due to hamcrest only doing string matches
        Instant start = Instant.parse(JsonPath.read(json, "$.startDate"));
        Instant end = Instant.parse(JsonPath.read(json, "$.endDate"));
        Assertions.assertThat(start).isEqualTo(sampleDate);
        Assertions.assertThat(end).isEqualTo(sampleDate);
    }

    @Test
    void updateMatchday_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/matchdays/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Matchday1"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchday() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteMatchday_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/matchdays/" + UUID.randomUUID()))
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

    private MvcResult createMatchday() throws Exception {
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
    //endregion
}
