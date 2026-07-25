package de.dtfb.sportshub.backend.teamparticipation;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The approval queue: {@code GET /v1/team-participations/pending?federationId=} returns the
 * SUBMITTED rosters in that federation only — DRAFT/CONFIRMED and other regions are excluded. A
 * region/global admin (gated by {@code canManageRegion}) sees every league's queue; a
 * {@code league_admin} sees only their own league(s) within the region, per
 * {@code canViewPendingApprovals}/{@code leagueAdminLeagueIdsInRegion}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TeamParticipationApprovalsIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired FederationRepository federationRepository;
    @Autowired SeasonRepository seasonRepository;
    @Autowired LeagueRepository leagueRepository;
    @Autowired ClubRepository clubRepository;
    @Autowired TeamRepository teamRepository;
    @Autowired TeamParticipationRepository participationRepository;
    @Autowired PlayerRepository playerRepository;
    @Autowired RoleAssignmentRepository roleAssignmentRepository;

    @Test
    void pending_listsSubmittedRostersInTheRegionOnly() throws Exception {
        Federation fed1 = federation("Bayern");
        Federation fed2 = federation("Hessen");
        Team team = team("TFC München 1", fed1);
        League l1 = league(season(fed1));
        League l2 = league(season(fed2));
        TeamParticipation submitted = participation(team, l1, RosterStatus.SUBMITTED);
        participation(team, l1, RosterStatus.DRAFT);      // not submitted → excluded
        participation(team, l2, RosterStatus.SUBMITTED);  // other region → excluded

        mockMvc.perform(get("/v1/team-participations/pending")
                .param("federationId", fed1.getId()).with(jwtFor("admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(submitted.getId()))
            .andExpect(jsonPath("$[0].rosterStatus").value("SUBMITTED"));
    }

    @Test
    void pending_forbiddenWithoutRegionAuthority() throws Exception {
        Federation fed = federation("NRW");
        playerRepository.save(player("outsider"));
        mockMvc.perform(get("/v1/team-participations/pending")
                .param("federationId", fed.getId()).with(jwtFor("outsider")))
            .andExpect(status().isForbidden());
    }

    @Test
    void pending_leagueAdminSeesOnlyTheirOwnLeague() throws Exception {
        Federation fed = federation("Bayern");
        Team team = team("TFC München 1", fed);
        League ownLeague = league(season(fed));
        League otherLeague = league(season(fed));
        TeamParticipation ownSubmitted = participation(team, ownLeague, RosterStatus.SUBMITTED);
        participation(team, otherLeague, RosterStatus.SUBMITTED); // a league they don't administer → excluded

        RequestPostProcessor leagueAdmin = grantLeagueAdmin("liga-own", ownLeague.getId());
        mockMvc.perform(get("/v1/team-participations/pending")
                .param("federationId", fed.getId()).with(leagueAdmin))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(ownSubmitted.getId()));
    }

    @Test
    void pending_leagueAdminOfAnotherRegionForbidden() throws Exception {
        Federation fed = federation("Bayern");
        Federation otherFed = federation("Hessen");
        League leagueInOtherFed = league(season(otherFed));

        RequestPostProcessor leagueAdmin = grantLeagueAdmin("liga-other-region", leagueInOtherFed.getId());
        mockMvc.perform(get("/v1/team-participations/pending")
                .param("federationId", fed.getId()).with(leagueAdmin))
            .andExpect(status().isForbidden());
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

    /** Seed a player with a single LEAGUE_ADMIN grant on {@code leagueId} and return its JWT. */
    private RequestPostProcessor grantLeagueAdmin(String dtfbId, String leagueId) {
        Player savedPlayer = playerRepository.save(player(dtfbId));
        RoleAssignment grant = new RoleAssignment();
        grant.setPlayer(savedPlayer);
        grant.setRole(Role.LEAGUE_ADMIN);
        grant.setScopeType(ScopeType.LEAGUE);
        grant.setScopeId(leagueId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }

    private Federation federation(String name) {
        Federation fed = new Federation();
        fed.setName(name);
        return federationRepository.save(fed);
    }

    private Season season(Federation federation) {
        Season season = new Season();
        season.setName("2025");
        season.setFederation(federation);
        season.setRegistrationOpensAt(LocalDate.of(2020, 1, 1));
        return seasonRepository.save(season);
    }

    private League league(Season season) {
        League league = new League();
        league.setName("Bayernliga");
        league.setSeason(season);
        return leagueRepository.save(league);
    }

    private Team team(String name, Federation federation) {
        Club club = new Club();
        club.setName("Club");
        club.setFederationId(federation.getId());
        club.setActive(true);
        club = clubRepository.save(club);
        Team team = new Team();
        team.setName(name);
        team.setClub(club);
        return teamRepository.save(team);
    }

    private TeamParticipation participation(Team team, League league, RosterStatus status) {
        TeamParticipation p = new TeamParticipation();
        p.setTeam(team);
        p.setLeague(league);
        p.setRosterStatus(status);
        return participationRepository.save(p);
    }
    // endregion
}
