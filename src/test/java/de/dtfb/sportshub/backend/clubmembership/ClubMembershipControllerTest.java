package de.dtfb.sportshub.backend.clubmembership;

import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerGender;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.AuthorizedControllerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Club membership: the precondition for a player being rostered onto one of a club's teams (see
 * {@code RosterServiceClubMembershipTest}). Join is idempotent, leave 404s when there's nothing
 * active to end, and both are gated by {@code @authz.canManageClub}.
 */
class ClubMembershipControllerTest extends AuthorizedControllerTest {

    @Autowired
    private PlayerRepository playerRepository;

    private String createPlayer() {
        Player player = new Player();
        player.setBirthYear(1990);
        player.setGender(PlayerGender.MALE);
        player.setFirstName("Test");
        player.setLastName("Player");
        return playerRepository.save(player).getId();
    }

    @Test
    void join_addsThePlayerToTheClubsMemberList() throws Exception {
        String clubId = createClub();
        String playerId = createPlayer();

        mockMvc.perform(post("/v1/clubs/" + clubId + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"playerId": "%s"}
                    """, playerId)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/v1/admin/players").param("clubId", clubId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == '" + playerId + "')]").exists());
    }

    @Test
    void join_isIdempotent() throws Exception {
        String clubId = createClub();
        String playerId = createPlayer();

        joinClub(playerId, clubId);
        joinClub(playerId, clubId); // no-op, must not throw or duplicate

        mockMvc.perform(get("/v1/admin/players").param("clubId", clubId))
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void leave_removesThePlayerFromTheClubsMemberList() throws Exception {
        String clubId = createClub();
        String playerId = createPlayer();
        joinClub(playerId, clubId);

        mockMvc.perform(delete("/v1/clubs/" + clubId + "/members/" + playerId))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/admin/players").param("clubId", clubId))
            .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void leave_whenNotAMember_isNotFound() throws Exception {
        String clubId = createClub();
        String playerId = createPlayer();

        mockMvc.perform(delete("/v1/clubs/" + clubId + "/members/" + playerId))
            .andExpect(status().isNotFound());
    }
}
