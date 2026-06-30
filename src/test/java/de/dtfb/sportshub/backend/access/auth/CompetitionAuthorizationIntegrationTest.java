package de.dtfb.sportshub.backend.access.auth;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tier C, end-to-end: exercises the REAL authorization stack (no mocked {@code @authz}) to prove an
 * {@code competition_organizer} may run the competition under their event, resolving each entity up the
 * spine to its owning Competition — and that an outsider, or an organizer of a *different* event, may not.
 *
 * <p>The chain is built by the bootstrap admin (dev profile seeds {@code dtfb_id="admin"} as global
 * ADMIN); other actors are seeded as players with a single {@code COMPETITION_ORGANIZER} grant.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompetitionAuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");

    private String competitionId;
    private String disciplineId;
    private String stageId;
    private String poolId;
    private String roundId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = createFederation();
        String seasonId = create("/v1/seasons", "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\"}");
        competitionId = create("/v1/competitions", "{\"name\":\"Bundesliga\",\"seasonId\":\"" + seasonId + "\"}");
        String categoryId = create("/v1/category", "{\"name\":\"Herren\",\"shortName\":\"H\"}");
        disciplineId = create("/v1/disciplines",
            "{\"name\":\"Einzel\",\"competitionId\":\"" + competitionId + "\",\"categoryId\":\"" + categoryId + "\"}");
        stageId = create("/v1/stages", "{\"name\":\"Vorrunde\",\"disciplineId\":\"" + disciplineId + "\"}");
        poolId = create("/v1/pools",
            "{\"name\":\"Pool1\",\"tournamentMode\":\"SWISS\",\"poolState\":\"READY\",\"stageId\":\"" + stageId + "\"}");
        roundId = create("/v1/rounds", "{\"name\":\"Runde1\",\"index\":1,\"poolId\":\"" + poolId + "\"}");
    }

    @Test
    void eventOrganizer_mayRunTheCompetitionUnderTheirEvent() throws Exception {
        RequestPostProcessor organizer = grantEventOrganizer("organizer", competitionId);

        // Shallow resolution (Discipline → Event) and progressively deeper walks all resolve to the
        // event the organizer was appointed to.
        mockMvc.perform(post("/v1/stages").with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Hauptrunde\",\"disciplineId\":\"" + disciplineId + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/pools").with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Pool2\",\"tournamentMode\":\"SWISS\",\"poolState\":\"READY\",\"stageId\":\"" + stageId + "\"}"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/v1/rounds").with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Runde2\",\"index\":2,\"poolId\":\"" + poolId + "\"}"))
            .andExpect(status().isCreated());
        // Deep walk: Round → Pool → Stage → Discipline → Event.
        mockMvc.perform(put("/v1/rounds/" + roundId).with(organizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Umbenannt\",\"index\":1,\"poolId\":\"" + poolId + "\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void outsider_withoutAnyRole_isForbidden() throws Exception {
        playerRepository.save(player("outsider"));
        mockMvc.perform(post("/v1/stages").with(jwtFor("outsider")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"disciplineId\":\"" + disciplineId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizerOfADifferentEvent_isForbidden() throws Exception {
        String otherEventId = create("/v1/competitions",
            "{\"name\":\"Other\",\"seasonId\":\"" + create("/v1/seasons",
                "{\"name\":\"2026\",\"federationId\":\"" + createFederation() + "\"}") + "\"}");
        RequestPostProcessor otherOrganizer = grantEventOrganizer("otherOrganizer", otherEventId);

        mockMvc.perform(post("/v1/stages").with(otherOrganizer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"disciplineId\":\"" + disciplineId + "\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(post("/v1/stages").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nope\",\"disciplineId\":\"" + disciplineId + "\"}"))
            .andExpect(status().isUnauthorized());
    }

    // --- helpers ---

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static Player player(String dtfbId) {
        Player player = new Player();
        player.setDtfbId(dtfbId);
        return player;
    }

    /** Seed a player with a single COMPETITION_ORGANIZER grant on {@code competitionId} and return its JWT. */
    private RequestPostProcessor grantEventOrganizer(String dtfbId, String competitionId) {
        Player player = playerRepository.save(player(dtfbId));
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(player);
        grant.setRole(Role.COMPETITION_ORGANIZER);
        grant.setScopeType(ScopeType.COMPETITION);
        grant.setScopeId(competitionId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }

    private String createFederation() throws Exception {
        return create("/v1/federation", "{\"name\":\"Testverband\"}");
    }

    /** POST as admin and return the created id. */
    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
