package de.dtfb.sportshub.backend.teamparticipation;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.clubmembership.ClubMembership;
import de.dtfb.sportshub.backend.clubmembership.ClubMembershipRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.support.TestIds;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SPO-28, cross-season identity: after copy-forward the new season has its own copies of the team
 * and of the league's rules, linked to last season's but independent of them. Uses the real
 * authorization stack (JWT + role assignment) for the captain case.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CopyForwardAcrossSeasonsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    ClubRepository clubRepository;

    @Autowired
    TeamRepository teamRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    ClubMembershipRepository clubMembershipRepository;

    private static final RequestPostProcessor ADMIN = jwtFor("admin");

    private String teamId;
    private String sourceLeagueId;
    private String targetParticipationId;
    private String targetTeamId;
    private String targetLeagueId;

    @BeforeEach
    void setup() throws Exception {
        String federationId = create("/v1/federations", "{\"name\":\"Testverband\"}");
        String sourceSeasonId = create("/v1/seasons",
            "{\"name\":\"2025\",\"federationId\":\"" + federationId + "\",\"registrationOpensAt\":\"2020-01-01\"}");
        String targetSeasonId = create("/v1/seasons",
            "{\"name\":\"2026\",\"federationId\":\"" + federationId + "\",\"registrationOpensAt\":\"2020-01-01\"}");
        String categoryId = create("/v1/categories",
            "{\"name\":\"Herren\",\"shortName\":\"" + TestIds.unique("H") + "\"}");
        sourceLeagueId = create("/v1/leagues",
            "{\"name\":\"Liga\",\"seasonId\":\"" + sourceSeasonId + "\",\"categoryId\":\"" + categoryId + "\"}");
        teamId = seedTeam(federationId);
        create("/v1/team-participations", "{\"teamId\":\"" + teamId + "\",\"leagueId\":\"" + sourceLeagueId + "\"}");

        mockMvc.perform(post("/v1/seasons/" + targetSeasonId + "/copy-forward").with(ADMIN)
                .param("from", sourceSeasonId))
            .andExpect(status().isOk());

        String participations = mockMvc.perform(get("/v1/team-participations").with(ADMIN)
                .param("seasonId", targetSeasonId))
            .andExpect(jsonPath("$.length()").value(1))
            .andReturn().getResponse().getContentAsString();
        targetParticipationId = JsonPath.read(participations, "$[0].id");
        targetTeamId = JsonPath.read(participations, "$[0].teamId");
        targetLeagueId = JsonPath.read(participations, "$[0].leagueId");
    }

    @Test
    void captainGrantedLastSeason_mayEditTheNewSeasonsRoster() throws Exception {
        RequestPostProcessor captain = teamAdmin("captain-across", teamId);

        mockMvc.perform(post("/v1/team-participations/" + targetParticipationId + "/roster").with(captain)
                .contentType(MediaType.APPLICATION_JSON).content("{\"playerId\": \"player-test\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void renamingTheNewSeasonsTeam_keepsLastSeasonsName() throws Exception {
        assertThat(targetTeamId).isNotEqualTo(teamId);

        mockMvc.perform(put("/v1/teams/" + targetTeamId).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Team (neu)\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/teams/" + teamId).with(ADMIN))
            .andExpect(jsonPath("$.name").value("Team"));
        mockMvc.perform(get("/v1/teams/" + targetTeamId).with(ADMIN))
            .andExpect(jsonPath("$.name").value("Team (neu)"));
    }

    @Test
    void newSeasonsLeague_getsItsOwnCopyOfLastSeasonsRules() throws Exception {
        String sourceRules = JsonPath.read(mockMvc.perform(get("/v1/leagues/" + sourceLeagueId).with(ADMIN))
            .andReturn().getResponse().getContentAsString(), "$.ruleSetId");
        String targetRules = JsonPath.read(mockMvc.perform(get("/v1/leagues/" + targetLeagueId).with(ADMIN))
            .andReturn().getResponse().getContentAsString(), "$.ruleSetId");
        assertThat(targetRules).isNotEqualTo(sourceRules);

        mockMvc.perform(put("/v1/league-rule-sets/" + targetRules).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Neu\", \"playSystem\": \"ROUND_ROBIN\", \"pointsWin\": 5}"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/v1/league-rule-sets/" + targetRules).with(ADMIN))
            .andExpect(jsonPath("$.pointsWin").value(5));
        mockMvc.perform(get("/v1/league-rule-sets/" + sourceRules).with(ADMIN))
            .andExpect(jsonPath("$.name").value(org.hamcrest.Matchers.not("Neu")));
    }

    // --- helpers ---

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private User upsertUser(String dtfbId) {
        return userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User user = new User();
            user.setDtfbId(dtfbId);
            return userRepository.save(user);
        });
    }

    /** Seed a user with a TEAM_ADMIN grant on the team's identity, and return its JWT. */
    private RequestPostProcessor teamAdmin(String dtfbId, String teamId) {
        Team team = teamRepository.findById(teamId).orElseThrow();
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(upsertUser(dtfbId));
        grant.setRole(Role.TEAM_ADMIN);
        grant.setScopeType(ScopeType.TEAM);
        grant.setScopeId(team.getTeamIdentityId());
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }

    /** Clubs have no create endpoint in tests' reach -- seed directly, with "player-test" as a member. */
    private String seedTeam(String federationId) {
        Club club = new Club();
        club.setName("Testverein");
        club.setFederationId(federationId);
        clubRepository.save(club);
        Team team = new Team();
        team.setName("Team");
        team.setClub(club);
        String id = teamRepository.save(team).getId();

        Player player = playerRepository.findById("player-test").orElseThrow();
        ClubMembership membership = new ClubMembership();
        membership.setPlayer(player);
        membership.setClub(club);
        membership.setJoinedAt(Instant.now());
        clubMembershipRepository.save(membership);
        return id;
    }

    private String create(String path, String body) throws Exception {
        String json = mockMvc.perform(post(path).with(ADMIN)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.id");
    }
}
