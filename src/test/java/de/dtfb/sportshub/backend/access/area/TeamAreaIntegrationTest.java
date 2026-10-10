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
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A {@code TEAM_ADMIN} grant surfaces a first-class "team" area from {@code /v1/auth/me/areas} — the
 * team admin's dedicated entry to their roster(s). Team areas come ONLY from explicit team grants;
 * they are never expanded from higher (region/club/global) scope, since there are far too many teams.
 * An admin above a team opens its area on demand via {@code /v1/auth/me/areas/teams/{id}} instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamAreaIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired RoleAssignmentRepository roleAssignmentRepository;
    @Autowired FederationRepository federationRepository;
    @Autowired ClubRepository clubRepository;
    @Autowired TeamRepository teamRepository;

    @Test
    void teamAdmin_getsTheirTeamArea() throws Exception {
        Federation fed = federation("Bayern");
        Club club = club("TFC München", fed.getId());
        Team team = team("TFC München 1", club);
        grantTeamAdmin("teamadmin", team.getTeamIdentityId());

        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("teamadmin")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> teamIds = JsonPath.read(json, "$.areas[?(@.type=='team')].id");
        List<String> teamNames = JsonPath.read(json, "$.areas[?(@.type=='team')].name");
        List<String> regionIds = JsonPath.read(json, "$.areas[?(@.type=='team')].regionId");
        assertThat(teamIds).containsExactly(team.getTeamIdentityId());
        assertThat(teamNames).containsExactly("TFC München 1");
        assertThat(regionIds).containsExactly(fed.getId());
    }

    /**
     * The root federation (docs/16-root-federation.md, seeded {@code fed-dtfb}) has no clubs of its
     * own, so its REGION_ADMIN's club-area expansion falls back to every OTHER federation's clubs
     * instead of the usual "just this federation's clubs" -- unlike a normal region admin (see
     * {@link #playerWithoutTeamGrant_getsNoTeamArea} 's sibling region for the ordinary case).
     */
    @Test
    void rootFederationRegionAdmin_getsEveryFederationsClubs() throws Exception {
        Federation root = federationRepository.findById("fed-dtfb").orElseThrow();
        Federation sub = federation("Sub-Verband");
        sub.setParentFederation(root);
        sub = federationRepository.save(sub);
        Club club = club("Fremder Verein", sub.getId());

        grant(userRepository.save(user("rootadmin")), Role.REGION_ADMIN, ScopeType.REGION, root.getId());

        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("rootadmin")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<String> clubIds = JsonPath.read(json, "$.areas[?(@.type=='club')].id");
        assertThat(clubIds).contains(club.getId());
    }

    @Test
    void playerWithoutTeamGrant_getsNoTeamArea() throws Exception {
        // A region admin reaches rosters via the placement path, not a team area.
        Federation fed = federation("Hessen");
        User user = userRepository.save(user("regionadmin"));
        grant(user, Role.REGION_ADMIN, ScopeType.REGION, fed.getId());

        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("regionadmin")))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        List<Object> teamAreas = JsonPath.read(json, "$.areas[?(@.type=='team')]");
        assertThat(teamAreas).isEmpty();
    }

    @Test
    void anAdminAboveTheTeam_opensItsAreaOnDemand_butTheListStaysUnexpanded() throws Exception {
        Federation fed = federation("Bayern-Ondemand");
        Club club = club("TFC Ondemand", fed.getId());
        Team team = team("TFC Ondemand 1", club);
        grant(userRepository.save(user("od-clubadmin")), Role.CLUB_ADMIN, ScopeType.CLUB, club.getId());
        grant(userRepository.save(user("od-regionadmin")), Role.REGION_ADMIN, ScopeType.REGION, fed.getId());

        for (String admin : List.of("od-clubadmin", "od-regionadmin")) {
            mockMvc.perform(get("/v1/auth/me/areas/teams/" + team.getTeamIdentityId()).with(jwtFor(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("team"))
                .andExpect(jsonPath("$.id").value(team.getTeamIdentityId()))
                .andExpect(jsonPath("$.name").value("TFC Ondemand 1"))
                .andExpect(jsonPath("$.regionId").value(fed.getId()));
        }
        String json = mockMvc.perform(get("/v1/auth/me/areas").with(jwtFor("od-clubadmin")))
            .andReturn().getResponse().getContentAsString();
        List<Object> teamAreas = JsonPath.read(json, "$.areas[?(@.type=='team')]");
        assertThat(teamAreas).isEmpty();
    }

    @Test
    void anAdminOfAnotherClub_cannotOpenTheTeamArea() throws Exception {
        Federation fed = federation("Hessen-Ondemand");
        Team team = team("Fremd 1", club("Fremder Club", fed.getId()));
        Club other = club("Eigener Club", fed.getId());
        grant(userRepository.save(user("od-otheradmin")), Role.CLUB_ADMIN, ScopeType.CLUB, other.getId());

        mockMvc.perform(get("/v1/auth/me/areas/teams/" + team.getTeamIdentityId()).with(jwtFor("od-otheradmin")))
            .andExpect(status().isForbidden());
    }

    // region helpers
    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static User user(String dtfbId) {
        User user = new User();
        user.setDtfbId(dtfbId);
        return user;
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

    private void grantTeamAdmin(String dtfbId, String teamIdentityId) {
        grant(userRepository.save(user(dtfbId)), Role.TEAM_ADMIN, ScopeType.TEAM, teamIdentityId);
    }

    private void grant(User user, Role role, ScopeType scopeType, String scopeId) {
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(user);
        grant.setRole(role);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
    }
    // endregion
}
