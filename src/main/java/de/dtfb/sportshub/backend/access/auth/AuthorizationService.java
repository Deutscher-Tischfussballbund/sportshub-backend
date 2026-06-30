package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.AccessRoles;
import de.dtfb.sportshub.backend.access.roleassignment.GrantRoleDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import de.dtfb.sportshub.backend.federation.Federation;
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
 * {@link RoleAssignment}s — NOT JWT roles, which are not mapped here. Each method resolves the
 * current player from the bearer token; callers are reached only after the filter chain has
 * already authenticated the request (chain baseline is {@code anyRequest().authenticated()}).
 *
 * <p>Scope hierarchy: a region is a federation; clubs belong to a region via
 * {@link Club#getFederationId()}; teams belong to a club via {@link Team#getClub()} (and thus to
 * that club's region). A region admin therefore administers every club and team within that region,
 * and a club admin administers the teams within that club.
 */
@Component("authz")
public class AuthorizationService {

    private final PlayerRegistryService registry;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final CompetitionRepository competitionRepository;
    private final SeasonRepository seasonRepository;
    private final LocationRepository locationRepository;
    private final MatchDayRepository matchDayRepository;
    private final CompetitionResolver competitionResolver;

    public AuthorizationService(PlayerRegistryService registry,
                                RoleAssignmentRepository roleAssignmentRepository,
                                ClubRepository clubRepository,
                                TeamRepository teamRepository,
                                CompetitionRepository competitionRepository,
                                SeasonRepository seasonRepository,
                                LocationRepository locationRepository,
                                MatchDayRepository matchDayRepository,
                                CompetitionResolver competitionResolver) {
        this.registry = registry;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.competitionRepository = competitionRepository;
        this.seasonRepository = seasonRepository;
        this.locationRepository = locationRepository;
        this.matchDayRepository = matchDayRepository;
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
     * so its region's admin (or a global admin) manages it. A season with no federation is global —
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

    /** May administer the given competition's meta (its region's admin, or global) — see the COMPETITION scope. */
    public boolean canManageCompetition(String competitionId) {
        return canManageScope(currentRoles(), ScopeType.COMPETITION, competitionId);
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

    /**
     * May administer the given discipline: a discipline belongs to an competition, so it inherits that
     * competition's region scope. (Its {@code Category} is a global classification and does not bear
     * scope.) This is region-config authority (admins) — for running the competition *under* a
     * discipline, see {@link #canOrganizeDiscipline}.
     */
    public boolean canManageDiscipline(String disciplineId) {
        Competition competition = competitionResolver.ofDiscipline(disciplineId);
        return canManageScope(currentRoles(), ScopeType.COMPETITION, competition == null ? null : competition.getId());
    }

    // --- Tier C: competition data. May run the competition the entity belongs to — the region/global
    // admin above its competition, OR an competition_organizer appointed to that competition. Each entity resolves to
    // its owning Competition via CompetitionResolver (Stage→Discipline→Competition, Match→MatchDay→…→Competition).

    public boolean canOrganizeDiscipline(String disciplineId) {
        return canOrganize(competitionResolver.ofDiscipline(disciplineId));
    }

    public boolean canOrganizeStage(String stageId) {
        return canOrganize(competitionResolver.ofStage(stageId));
    }

    public boolean canOrganizePool(String poolId) {
        return canOrganize(competitionResolver.ofPool(poolId));
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
     * Competition-data capability for the given competition: the region/global admin above it OR an
     * {@code competition_organizer} appointed to that competition. Distinct from {@link #canManageCompetition} (competition
     * meta — admins only); an organizer runs the competition, not the competition's place in the tree.
     */
    private boolean canOrganize(Competition competition) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        if (competition == null) {
            return false;
        }
        Federation region = competition.getSeason() == null ? null : competition.getSeason().getFederation();
        boolean regionAdmin = region != null && isRegionAdmin(roles, region.getId());
        return regionAdmin || isCompetitionOrganizer(roles, competition.getId());
    }

    private boolean isCompetitionOrganizer(List<RoleAssignment> roles, String competitionId) {
        return competitionId != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.COMPETITION_ORGANIZER && Objects.equals(ra.getScopeId(), competitionId));
    }

    /**
     * Tier D: may submit/confirm the league result of the given match day, gating the team-rep
     * workflow. The caller must represent a *participating* team (axis 🟦+🟨) — a {@code team_admin}
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
     * CRUD — admins above only), this also accepts the {@code team_admin} role itself, which exists
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
                // An competition belongs to its region via Competition -> Season -> Federation; the region
                // admin of that region administers the competition (e.g. to appoint an competition organizer).
                Competition competition = scopeId == null ? null : competitionRepository.findById(scopeId).orElse(null);
                Federation region = competition == null || competition.getSeason() == null
                    ? null : competition.getSeason().getFederation();
                yield region != null && isRegionAdmin(roles, region.getId());
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
