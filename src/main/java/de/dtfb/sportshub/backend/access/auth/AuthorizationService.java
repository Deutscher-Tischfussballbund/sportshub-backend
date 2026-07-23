package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.AccessRoles;
import de.dtfb.sportshub.backend.access.roleassignment.GrantRoleDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.league.League;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRegistryService;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * Authorization checks exposed to {@code @PreAuthorize} SpEL as {@code @authz}
 * (e.g. {@code @PreAuthorize("@authz.canGrant(#dto)")}).
 *
 * <p>Bridges Spring Security to this app's authoritative role model: DB-backed, scoped
 * {@link RoleAssignment}s - NOT JWT roles, which are not mapped here. Each method resolves the
 * current player from the bearer token; callers are reached only after the filter chain has
 * already authenticated the request (chain baseline is {@code anyRequest().authenticated()}).
 *
 * <p>Scope hierarchy: a region is a federation; clubs belong to a region via
 * {@link Club#getFederationId()}; teams belong to a club via {@link Team#getClub()} (and thus to
 * that club's region). A region admin therefore administers every club and team within that region,
 * and a club admin administers the teams within that club.
 *
 * <p>Note: the {@code COMPETITION} scope / {@code COMPETITION_ORGANIZER} role gate a {@link League}
 * (the scope's {@code scopeId} is a league id); the scope/role keep their generic names.
 */
@Component("authz")
public class AuthorizationService {

    private final PlayerRegistryService registry;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final LocationRepository locationRepository;
    private final MatchDayRepository matchDayRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TierRepository tierRepository;
    private final LeagueRuleSetRepository leagueRuleSetRepository;
    private final CompetitionResolver competitionResolver;

    public AuthorizationService(PlayerRegistryService registry,
                                RoleAssignmentRepository roleAssignmentRepository,
                                ClubRepository clubRepository,
                                TeamRepository teamRepository,
                                LeagueRepository leagueRepository,
                                SeasonRepository seasonRepository,
                                LocationRepository locationRepository,
                                MatchDayRepository matchDayRepository,
                                TeamParticipationRepository teamParticipationRepository,
                                TierRepository tierRepository,
                                LeagueRuleSetRepository leagueRuleSetRepository,
                                CompetitionResolver competitionResolver) {
        this.registry = registry;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.locationRepository = locationRepository;
        this.matchDayRepository = matchDayRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tierRepository = tierRepository;
        this.leagueRuleSetRepository = leagueRuleSetRepository;
        this.competitionResolver = competitionResolver;
    }

    /** Global DTFB administrator. */
    public boolean isAdmin() {
        return AccessRoles.isGlobalAdmin(currentRoles());
    }

    /** May administer the given region (federation). */
    public boolean canManageRegion(String regionId) {
        return canManageScope(currentRoles(), ScopeType.REGION, regionId);
    }

    /** May administer the given club (global admin, or admin of the club's region, or of the club). */
    public boolean canManageClub(String clubId) {
        return canManageScope(currentRoles(), ScopeType.CLUB, clubId);
    }

    /** May administer the given team (global/region/club admin above it). */
    public boolean canManageTeam(String teamId) {
        return canManageScope(currentRoles(), ScopeType.TEAM, teamId);
    }

    /**
     * May administer the given season: a season belongs to a region via {@link Season#getFederation()},
     * so its region's admin (or a global admin) manages it. A season with no federation is global -
     * only a global admin may manage it.
     */
    public boolean canManageSeason(String seasonId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        Season season = seasonId == null ? null : seasonRepository.findById(seasonId).orElse(null);
        Federation region = season == null ? null : season.getFederation();
        return region != null && isRegionAdmin(roles, region.getId());
    }

    /**
     * May administer the given league's meta: its region's admin (or global), OR a
     * {@code competition_organizer} appointed to it -- a league admin has full authority over their
     * one league, not just its Tier/Group/MatchDay data (see {@link #canOrganize}). See the
     * COMPETITION scope.
     */
    public boolean canManageLeague(String leagueId) {
        return canManageScope(currentRoles(), ScopeType.COMPETITION, leagueId);
    }

    /**
     * May create a team participation for the given league: either the league's region admin
     * (admin-driven placement flow) OR a {@code team_admin} who represents the team being
     * registered (team self-registration). The team must be in the DTO; the season's
     * {@code registrationOpen} window is enforced by the frontend / caller.
     */
    public boolean canRegisterForLeague(String leagueId, String teamId) {
        if (canManageLeague(leagueId)) {
            return true;
        }
        List<RoleAssignment> roles = currentRoles();
        Team team = teamId == null ? null : teamRepository.findById(teamId).orElse(null);
        return canRepresent(roles, team);
    }

    /**
     * May administer the given tier: a tier belongs to a league and thus to that league's season and
     * region, so the region's admin (or a global admin) manages it - the same authority as editing
     * the league it hangs off. A {@code competition_organizer} of that league may too, for the same
     * reason {@link #canManageLeague} accepts one.
     */
    public boolean canManageTier(String tierId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        Tier tier = tierId == null ? null : tierRepository.findById(tierId).orElse(null);
        League league = tier == null ? null : tier.getLeague();
        Season season = league == null ? null : league.getSeason();
        Federation region = season == null ? null : season.getFederation();
        return (region != null && isRegionAdmin(roles, region.getId()))
            || (league != null && isCompetitionOrganizer(roles, league.getId()));
    }

    /**
     * May create/edit a league rule set owned by the given region. A {@code null} federation means a
     * DTFB-global template - only a global admin may manage it (handled by the admin short-circuit).
     */
    public boolean canManageRuleSet(String federationId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        return federationId != null && isRegionAdmin(roles, federationId);
    }

    /** May edit/delete the given rule set: the admin of its owning region (or global). */
    public boolean canManageRuleSetById(String ruleSetId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        LeagueRuleSet ruleSet = ruleSetId == null ? null : leagueRuleSetRepository.findById(ruleSetId).orElse(null);
        Federation federation = ruleSet == null ? null : ruleSet.getFederation();
        return federation != null && isRegionAdmin(roles, federation.getId());
    }

    /**
     * May administer the given team participation (placement): it belongs to a league and thus to
     * that league's season and region, so the region's admin (or a global admin) manages it. This is
     * region placement authority - the same scope as editing the season itself (section 6 of the
     * model) - plus a {@code competition_organizer} of that participation's league, as a full league
     * admin manages placements within their own league too.
     */
    public boolean canManageParticipation(String participationId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        TeamParticipation participation = participationId == null
            ? null : teamParticipationRepository.findById(participationId).orElse(null);
        League league = participation == null ? null : participation.getLeague();
        Season season = league == null ? null : league.getSeason();
        Federation region = season == null ? null : season.getFederation();
        return (region != null && isRegionAdmin(roles, region.getId()))
            || (league != null && isCompetitionOrganizer(roles, league.getId()));
    }

    /**
     * May edit/submit the roster of the given participation (L2): the {@code team_admin} of the
     * participation's team, or an admin above it (club/region/global). Same authority as representing
     * the team in the result flow - see {@link #canReportMatchDay}. A {@code competition_organizer} of
     * the participation's league may too, as a full league admin.
     */
    public boolean canEditRoster(String participationId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        TeamParticipation participation = participationId == null
            ? null : teamParticipationRepository.findById(participationId).orElse(null);
        Team team = participation == null ? null : participation.getTeam();
        League league = participation == null ? null : participation.getLeague();
        return canRepresent(roles, team) || (league != null && isCompetitionOrganizer(roles, league.getId()));
    }

    /**
     * May confirm/reopen the roster of the given participation (L2): an admin ABOVE the team
     * (club/region/global) - deliberately NOT the {@code team_admin} who submits, so the final say is
     * separate from the submission - or a {@code competition_organizer} of the participation's league,
     * as a full league admin.
     */
    public boolean canConfirmRoster(String participationId) {
        List<RoleAssignment> roles = currentRoles();
        TeamParticipation participation = participationId == null
            ? null : teamParticipationRepository.findById(participationId).orElse(null);
        Team team = participation == null ? null : participation.getTeam();
        League league = participation == null ? null : participation.getLeague();
        boolean adminAboveTeam = team != null && canManageScope(roles, ScopeType.TEAM, team.getId());
        return adminAboveTeam || (league != null && isCompetitionOrganizer(roles, league.getId()));
    }

    /**
     * May administer the given location: a venue belongs to a region via
     * {@link Location#getFederation()} (or is global if region-less, then admin-only).
     */
    public boolean canManageLocation(String locationId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        Location location = locationId == null ? null : locationRepository.findById(locationId).orElse(null);
        Federation region = location == null ? null : location.getFederation();
        return region != null && isRegionAdmin(roles, region.getId());
    }

    // --- Tier C: competition data. May run the league the entity belongs to - the region/global
    // admin above its league, OR a competition_organizer appointed to that league. Each entity resolves
    // to its owning League via CompetitionResolver (Group -> Tier -> League, Match -> MatchDay -> ... -> League).

    public boolean canOrganizeTier(String tierId) {
        return canOrganize(competitionResolver.ofTier(tierId));
    }

    public boolean canOrganizeGroup(String groupId) {
        return canOrganize(competitionResolver.ofGroup(groupId));
    }

    public boolean canOrganizeRound(String roundId) {
        return canOrganize(competitionResolver.ofRound(roundId));
    }

    public boolean canOrganizeMatchDay(String matchDayId) {
        return canOrganize(competitionResolver.ofMatchDay(matchDayId));
    }

    public boolean canOrganizeMatch(String matchId) {
        return canOrganize(competitionResolver.ofMatch(matchId));
    }

    public boolean canOrganizeMatchSet(String matchSetId) {
        return canOrganize(competitionResolver.ofMatchSet(matchSetId));
    }

    public boolean canOrganizeMatchEvent(String matchEventId) {
        return canOrganize(competitionResolver.ofMatchEvent(matchEventId));
    }

    /**
     * Competition-data capability for the given league: the region/global admin above it OR a
     * {@code competition_organizer} appointed to that league. Distinct from {@link #canManageLeague}
     * (league meta - admins only); an organizer runs the league, not the league's place in the tree.
     */
    private boolean canOrganize(League league) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        if (league == null) {
            return false;
        }
        Federation region = league.getSeason() == null ? null : league.getSeason().getFederation();
        boolean regionAdmin = region != null && isRegionAdmin(roles, region.getId());
        return regionAdmin || isCompetitionOrganizer(roles, league.getId());
    }

    private boolean isCompetitionOrganizer(List<RoleAssignment> roles, String leagueId) {
        return leagueId != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.COMPETITION_ORGANIZER && Objects.equals(ra.getScopeId(), leagueId));
    }

    /**
     * Tier D: may submit/confirm the league result of the given match day, gating the team-rep
     * workflow. The caller must represent a participating team - a {@code team_admin}
     * of {@code teamHome}/{@code teamAway}, or an admin above that team (club/region/global). The
     * submitter-vs-opponent distinction on confirm is enforced in {@code MatchDayService} (a person
     * may not confirm their own submission); this gate only establishes affiliation.
     */
    public boolean canReportMatchDay(String matchDayId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        MatchDay matchDay = matchDayId == null ? null : matchDayRepository.findById(matchDayId).orElse(null);
        if (matchDay == null) {
            return false;
        }
        return canRepresent(roles, matchDay.getTeamHome()) || canRepresent(roles, matchDay.getTeamAway());
    }

    /**
     * Whether the current player may act for {@code team}: its {@code team_admin}, or an admin above
     * it (club admin of its club, region admin of its region). Unlike {@link #canManageTeam} (team
     * CRUD - admins above only), this also accepts the {@code team_admin} role itself, which exists
     * precisely to act for one team in the result flow.
     */
    private boolean canRepresent(List<RoleAssignment> roles, Team team) {
        if (team == null) {
            return false;
        }
        boolean teamAdmin = roles.stream().anyMatch(ra ->
            ra.getRole() == Role.TEAM_ADMIN && Objects.equals(ra.getScopeId(), team.getId()));
        Club club = team.getClub();
        return teamAdmin
            || (club != null && (isRegionAdmin(roles, club.getFederationId()) || isClubAdmin(roles, club.getId())));
    }

    /** May grant the role/scope in {@code dto}: the granter must administer the target scope. */
    public boolean canGrant(GrantRoleDto dto) {
        if (dto == null || dto.role() == null) {
            return false;
        }
        return canManageScope(currentRoles(), dto.role().scopeType(), dto.scopeId());
    }

    /** May revoke the given assignment: the revoker must administer that assignment's scope. */
    public boolean canRevoke(String assignmentId) {
        List<RoleAssignment> roles = currentRoles();
        return roleAssignmentRepository.findById(assignmentId)
            // Unknown id: only a global admin may proceed (and then get a 404), others are denied.
            .map(ra -> canManageScope(roles, ra.getScopeType(), ra.getScopeId()))
            .orElseGet(() -> AccessRoles.isGlobalAdmin(roles));
    }

    /**
     * Whether the current player administers the given scope. Global admins administer everything;
     * a region admin administers that region and every club/team within it; a club admin administers
     * that club.
     */
    private boolean canManageScope(List<RoleAssignment> roles, ScopeType scopeType, String scopeId) {
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        return switch (scopeType) {
            case GLOBAL -> false;
            case REGION -> isRegionAdmin(roles, scopeId);
            case CLUB -> {
                Club club = scopeId == null ? null : clubRepository.findById(scopeId).orElse(null);
                yield club != null
                    && (isRegionAdmin(roles, club.getFederationId()) || isClubAdmin(roles, club.getId()));
            }
            case TEAM -> {
                Team team = scopeId == null ? null : teamRepository.findById(scopeId).orElse(null);
                Club club = team == null ? null : team.getClub();
                yield club != null
                    && (isRegionAdmin(roles, club.getFederationId()) || isClubAdmin(roles, club.getId()));
            }
            case COMPETITION -> {
                // A league belongs to its region via League -> Season -> Federation; the region admin
                // of that region administers the league (e.g. to appoint a competition organizer) --
                // and, as a full league admin, a competition_organizer already appointed to it
                // administers it too (e.g. to appoint a co-organizer), same pattern as a club admin
                // granting team_admin within their own club.
                League league = scopeId == null ? null : leagueRepository.findById(scopeId).orElse(null);
                Federation region = league == null || league.getSeason() == null
                    ? null : league.getSeason().getFederation();
                yield (region != null && isRegionAdmin(roles, region.getId()))
                    || isCompetitionOrganizer(roles, scopeId);
            }
        };
    }

    private boolean isRegionAdmin(List<RoleAssignment> roles, String regionId) {
        return regionId != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.REGION_ADMIN && Objects.equals(ra.getScopeId(), regionId));
    }

    private boolean isClubAdmin(List<RoleAssignment> roles, String clubId) {
        return clubId != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.CLUB_ADMIN && Objects.equals(ra.getScopeId(), clubId));
    }

    private List<RoleAssignment> currentRoles() {
        Player player = registry.currentPlayer(currentJwt());
        return roleAssignmentRepository.findByPlayer(player);
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("No authenticated JWT principal");
    }
}
