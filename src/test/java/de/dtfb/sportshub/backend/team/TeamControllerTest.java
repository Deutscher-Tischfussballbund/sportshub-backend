package de.dtfb.sportshub.backend.team;

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
class TeamControllerTest {

    @Autowired
    MockMvc mockMvc;

    String url;

    @BeforeEach
    void setupEach() throws Exception {
        MvcResult team = createTeam();
        url = team.getResponse().getHeader("Location");
        assert url != null;
    }

    @Test
    void getAllTeams() throws Exception {
        mockMvc.perform(get("/api/v1/teams"))
            .andExpect(status().isOk());
    }

    @Test
    void getTeam_expectException() throws Exception {
        mockMvc.perform(get("/api/v1/teams/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createAndGetTeam() throws Exception {
        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Hand und Foos"));
    }

    @Test
    void updateTeam() throws Exception {
        mockMvc.perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Jetlag"}
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Jetlag"));
    }

    @Test
    void updateTeam_expectException() throws Exception {
        mockMvc.perform(put("/api/v1/teams/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Tabledancers"}
                    """))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTeam() throws Exception {
        mockMvc.perform(delete(url))
            .andExpect(status().isOk());

        mockMvc.perform(get(url))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteTeam_expectException() throws Exception {
        mockMvc.perform(delete("/api/v1/teams/" + UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    /**
     * =========================================================
     * helper operations
     * =========================================================
     */

    //region helpers
    private MvcResult createTeam() throws Exception {
        return mockMvc.perform(post("/api/v1/teams")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"name": "Hand und Foos"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
    }
    //endregion
}
