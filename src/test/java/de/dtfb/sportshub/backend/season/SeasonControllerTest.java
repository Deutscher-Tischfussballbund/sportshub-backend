package de.dtfb.sportshub.backend.season;

import com.jayway.jsonpath.JsonPath;
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
class SeasonControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void getAllSeasons() throws Exception {
        mockMvc.perform(get("/api/v1/seasons")).andExpect(status().isOk());
    }

    @Test
    void getSeason_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/seasons/" + UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    void createAndGetSeason() throws Exception {
        MvcResult result = createSeason();
        String location = result.getResponse().getHeader("Location");
        assert location != null;

        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("2000"));
    }

    @Test
    void updateSeason() throws Exception {
        MvcResult result = createSeason();
        String location = result.getResponse().getHeader("Location");
        assert location != null;

        mockMvc.perform(put(location)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "2024"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get(location)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("2024"));
    }

    @Test
    void updateSeason_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/seasons/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "2024"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteSeason() throws Exception {
        MvcResult result = createSeason();
        String json = result.getResponse().getContentAsString();

        String uuid = JsonPath.read(json, "$.uuid");
        mockMvc.perform(delete("/api/v1/seasons/{uuid}", uuid))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/seasons/{uuid}", uuid))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteSeason_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/seasons/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    private MvcResult createSeason() throws Exception {
        return mockMvc.perform(post("/api/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "2000"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    }
}
