package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin-frontend player directory read/write. Player is a single, un-duplicated row (see
 * {@code docs/14-team-player-versioning.md}) -- editing it records a change-history entry per
 * changed field instead of cloning the row.
 */
class PlayerAdminControllerTest extends AuthorizedControllerTest {

    @Autowired
    private PlayerRepository playerRepository;

    private String createPlayer(String firstName, String lastName) {
        Player player = new Player();
        player.setFirstName(firstName);
        player.setLastName(lastName);
        player.setNationality("DE");
        return playerRepository.save(player).getId();
    }

    @Test
    void players_listsSeededPlayers() throws Exception {
        mockMvc.perform(get("/v1/admin/players"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void update_rewritesTrackedFields_andRecordsHistory() throws Exception {
        String id = createPlayer("Lukas", "Bauer");

        mockMvc.perform(put("/v1/admin/players/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "Lucas", "lastName": "Bauer", "nationality": "AT", "active": true}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.firstName").value("Lucas"))
            .andExpect(jsonPath("$.nationality").value("AT"));

        mockMvc.perform(get("/v1/admin/players/" + id + "/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2)) // firstName + nationality changed; lastName didn't
            .andExpect(jsonPath("$[?(@.fieldName == 'firstName')].oldValue").value("Lukas"))
            .andExpect(jsonPath("$[?(@.fieldName == 'firstName')].newValue").value("Lucas"));
    }

    @Test
    void update_unknownPlayer_isNotFound() throws Exception {
        mockMvc.perform(put("/v1/admin/players/does-not-exist")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"firstName": "X", "lastName": "Y", "active": true}
                    """))
            .andExpect(status().isNotFound());
    }
}
