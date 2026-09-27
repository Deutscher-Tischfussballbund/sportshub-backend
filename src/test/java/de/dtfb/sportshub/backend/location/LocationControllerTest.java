package de.dtfb.sportshub.backend.location;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class LocationControllerTest extends de.dtfb.sportshub.backend.support.AuthorizedControllerTest {

    String url;

    @Autowired
    LocationRepository locationRepository;

    @Autowired
    MatchDayRepository matchDayRepository;

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult location = createLocation();
        url = location.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllLocations() throws Exception {
        mockMvc.perform(get("/v1/locations"))
            .andExpect(status().isOk());
    }

    @Test
    void getLocation_expectException() throws Exception {
        mockMvc.perform(get("/v1/locations/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetLocation() throws Exception {
        mockMvc.perform(get(url)).andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("MKK"))
            .andExpect(jsonPath("$.address").value("Flensburg, Musterstraße 1"));
    }

    @Test
    void updateLocation() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Sidekick",
                            "address": "Hamburg, Große Freiheit 222"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Sidekick"));
    }

    @Test
    void updateLocation_expectException() throws Exception {
        mockMvc.perform(put("/v1/locations/" + NanoIdUtils.randomNanoId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Kixx",
                            "address": "Hamburg, Musterallee 1"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteLocation() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteLocation_expectException() throws Exception {
        mockMvc.perform(delete("/v1/locations/" + NanoIdUtils.randomNanoId()))
            .andExpect(status().isNotFound());
    }

    @Test
    void regionFilter_returnsTheRegionsOwnAndGlobalVenues() throws Exception {
        String regionA = createFederation();
        String regionB = createFederation();
        String inA = createVenue("Halle A", regionA);
        String inB = createVenue("Halle B", regionB);
        String global = createVenue("Bundesstützpunkt", null);

        String json = mockMvc.perform(get("/v1/locations").param("federationId", regionA))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<String> ids = JsonPath.read(json, "$[*].id");
        Assertions.assertThat(ids).contains(inA, global).doesNotContain(inB);
    }

    @Test
    void updateLocation_cannotMoveItToAnotherRegion() throws Exception {
        String regionA = createFederation();
        String regionB = createFederation();
        String venue = createVenue("Halle A", regionA);

        mockMvc.perform(put("/v1/locations/" + venue)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Halle A\",\"federationId\":\"%s\"}", regionB)))
            .andExpect(status().isBadRequest());
        // Same region (or none sent) is fine.
        mockMvc.perform(put("/v1/locations/" + venue)
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("{\"name\":\"Halle A2\",\"federationId\":\"%s\"}", regionA)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.federationId").value(regionA));
    }

    @Test
    void deleteLocation_usedByAFixture_isRejectedWithConflict() throws Exception {
        String venue = createVenue("Spielhalle", null);
        MatchDay fixture = new MatchDay();
        fixture.setLocation(locationRepository.findById(venue).orElseThrow());
        fixture.setStartDate(Instant.parse("2027-03-20T09:00:00Z"));
        matchDayRepository.save(fixture);

        mockMvc.perform(delete("/v1/locations/" + venue))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("LOCATION_IN_USE"))
            .andExpect(jsonPath("$.fixtureCount").value(1));
        Assertions.assertThat(locationRepository.existsById(venue)).isTrue();
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private MvcResult createLocation() throws Exception {
        return mockMvc.perform(post("/v1/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "MKK",
                            "address": "Flensburg, Musterstraße 1"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    }
    private String createVenue(String name, String federationId) throws Exception {
        String body = federationId == null
            ? String.format("{\"name\":\"%s\"}", name)
            : String.format("{\"name\":\"%s\",\"federationId\":\"%s\"}", name, federationId);
        String json = mockMvc.perform(post("/v1/locations").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
    //endregion
}
