package de.dtfb.sportshub.backend.location;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    MockMvc mockMvc;

    String url;

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult location = createLocation();
        url = location.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllLocations() throws Exception {
        mockMvc.perform(get("/api/v1/locations"))
            .andExpect(status().isOk());
    }

    @Test
    void getLocation_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/locations/" + UUID.randomUUID()))
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
        mockMvc.perform(put("/api/v1/locations/" + UUID.randomUUID())
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
        mockMvc.perform(delete("/api/v1/locations/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
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
    //endregion
}
