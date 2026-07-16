package de.dtfb.sportshub.backend.access.area;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code TEAM_ADMIN} grant surfaces a first-class "team" area from {@code /v1/auth/me/areas} — the
 * team admin's dedicated entry to their roster(s). Team areas come ONLY from explicit team grants;
 * they are never expanded from higher (region/club/global) scope, since there are far too many teams.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamAreaIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired PlayerRepository playerRepository;
    @Autowired RoleAssignmentRepository roleAssignmentRepository;
    @Autowired FederationRepository federationRepository;
    @Autowired ClubRepository clubRepository;
    @Autowired TeamRepository teamRepository;

    @Test
    void teamAdmin_getsTheirTeamArea() throws Exception {
        Federation fed = federation("Bayern");
        Club club = club("TFC München", fed.getId());
        Team team = team("TFC München 1", club);
        grantTeamAdmin("teamadmin", team.getId());

        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("teamadmin")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> teamIds = JsonPath.read(json, "$.areas[?(@.type=='team')].id");
        List<String> teamNames = JsonPath.read(json, "$.areas[?(@.type=='team')].name");
        List<String> regionIds = JsonPath.read(json, "$.areas[?(@.type=='team')].regionId");
        assertThat(teamIds).containsExactly(team.getId());
        assertThat(teamNames).containsExactly("TFC München 1");
        assertThat(regionIds).containsExactly(fed.getId());
    }

    @Test
    void playerWithoutTeamGrant_getsNoTeamArea() throws Exception {
        // A region admin reaches rosters via the placement path, not a team area.
        Federation fed = federation("Hessen");
        Player player = playerRepository.save(player("regionadmin"));
        grant(player, Role.REGION_ADMIN, ScopeType.REGION, fed.getId());

        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("regionadmin")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<Object> teamAreas = JsonPath.read(json, "$.areas[?(@.type=='team')]");
        assertThat(teamAreas).isEmpty();
    }

    // region helpers
    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static Player player(String dtfbId) {
        Player player = new Player();
        player.setDtfbId(dtfbId);
        return player;
    }

    private Federation federation(String name) {
        Federation fed = new Federation();
        fed.setName(name);
        return federationRepository.save(fed);
    }

    private Club club(String name, String federationId) {
        Club club = new Club();
        club.setName(name);
        club.setFederationId(federationId);
        club.setActive(true);
        return clubRepository.save(club);
    }

    private Team team(String name, Club club) {
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        return teamRepository.save(team);
    }

    private void grantTeamAdmin(String dtfbId, String teamId) {
        grant(playerRepository.save(player(dtfbId)), Role.TEAM_ADMIN, ScopeType.TEAM, teamId);
    }

    private void grant(Player player, Role role, ScopeType scopeType, String scopeId) {
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(player);
        grant.setRole(role);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
    }
    // endregion
}
