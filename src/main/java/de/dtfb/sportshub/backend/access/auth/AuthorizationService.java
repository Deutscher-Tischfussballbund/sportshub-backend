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
import de.dtfb.sportshub.backend.leaguerules.ApplyBlueprintRequest;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSetRepository;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultActor;
import de.dtfb.sportshub.backend.roster.RosterEntryRepository;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamDto;
import de.dtfb.sportshub.backend.team.TeamRepository;
import de.dtfb.sportshub.backend.team.TeamService;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRegistryService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
 * <p>The {@code TEAM} scope is identity-keyed, not row-id-keyed: {@link Team} is season-scoped (a
 * fresh row per season via copy-forward), so a role granted against one season's row must still
 * match every other season's copy of the same team. See {@link Team#getTeamIdentityId()}.
 *
 * <p>Note: the {@code LEAGUE} scope / {@code LEAGUE_ADMIN} role gate a {@link League} directly
 * (the scope's {@code scopeId} is the league identity, stable across season-copies -- SPO-28).
 */
@Component("authz")
public class AuthorizationService {

    private final UserRegistryService registry;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ClubRepository clubRepository;
    private final TeamRepository teamRepository;
    private final TeamService teamService;
    private final LeagueRepository leagueRepository;
    private final SeasonRepository seasonRepository;
    private final LocationRepository locationRepository;
    private final MatchDayRepository matchDayRepository;
    private final TeamParticipationRepository teamParticipationRepository;
    private final TierRepository tierRepository;
    private final LeagueRuleSetRepository leagueRuleSetRepository;
    private final CompetitionResolver competitionResolver;
    private final RosterEntryRepository rosterEntryRepository;

    public AuthorizationService(UserRegistryService registry,
                                RoleAssignmentRepository roleAssignmentRepository,
                                ClubRepository clubRepository,
                                TeamRepository teamRepository,
                                TeamService teamService,
                                LeagueRepository leagueRepository,
                                SeasonRepository seasonRepository,
                                LocationRepository locationRepository,
                                MatchDayRepository matchDayRepository,
                                TeamParticipationRepository teamParticipationRepository,
                                TierRepository tierRepository,
                                LeagueRuleSetRepository leagueRuleSetRepository,
                                CompetitionResolver competitionResolver,
                                RosterEntryRepository rosterEntryRepository) {
        this.registry = registry;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
        this.teamService = teamService;
        this.leagueRepository = leagueRepository;
        this.seasonRepository = seasonRepository;
        this.locationRepository = locationRepository;
        this.matchDayRepository = matchDayRepository;
        this.teamParticipationRepository = teamParticipationRepository;
        this.tierRepository = tierRepository;
        this.leagueRuleSetRepository = leagueRuleSetRepository;
        this.competitionResolver = competitionResolver;
        this.rosterEntryRepository = rosterEntryRepository;
    }

    /** Global DTFB administrator. */
    public boolean isAdmin() {
        return AccessRoles.isGlobalAdmin(currentRoles());
    }

    /**
     * Holds any admin role (global, region, club, or league admin) -- not {@code team_admin}. A
     * read-side visibility check (e.g. the divers league side on players), so it returns false
     * rather than throwing for a token without a {@code dtfb_id} (a service-account JWT).
     */
    public boolean hasAnyAdminRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication != null && authentication.getPrincipal() instanceof Jwt jwt)) {
            return false;
        }
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        if (dtfbId == null || dtfbId.isBlank()) {
            return false;
        }
        return AccessRoles.hasAnyAdminRole(currentRoles());
    }

    /** May administer the given region (federation). */
    public boolean canManageRegion(String regionId) {
        return canManageScope(currentRoles(), ScopeType.REGION, regionId);
    }

    /** May administer the given club (global admin, or admin of the club's region, or of the club). */
    public boolean canManageClub(String clubId) {
        return canManageScope(currentRoles(), ScopeType.CLUB, clubId);
    }

    /**
     * May administer the given team (global/region/club admin above it). {@code teamId} is a
     * specific row id (e.g. from a URL path); resolved to its {@link Team#getTeamIdentityId()} since
     * {@code TEAM} scope is keyed by identity, not row id -- a team is season-scoped (a fresh row per
     * season via copy-forward), so a role granted against one season's row must still match every
     * other season's copy of the same team.
     */
    public boolean canManageTeam(String teamId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        Team team = teamId == null ? null : teamRepository.findById(teamId).orElse(null);
        return team != null && canManageScope(roles, ScopeType.TEAM, team.getTeamIdentityId());
    }

    /**
     * May create the team described by {@code dto}: an admin of its club (the normal case), OR,
     * root-federation reuse (docs/16-root-federation.md), a region admin of the target season's OWN
     * federation -- narrowly lets a league organizer field a team for any club in their own
     * root-level league without granting general authority over that club.
     */
    public boolean canCreateTeam(TeamDto dto) {
        if (dto == null) {
            return false;
        }
        if (canManageClub(dto.getClubId())) {
            return true;
        }
        Season season = dto.getSeasonId() == null ? null : seasonRepository.findById(dto.getSeasonId()).orElse(null);
        Federation federation = season == null ? null : season.getFederation();
        return federation != null && isRegionAdmin(currentRoles(), federation.getId());
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
     * {@code league_admin} appointed to it -- a league admin has full authority over their
     * one league, not just its Tier/Group/MatchDay data (see {@link #canOrganize}). See the
     * LEAGUE scope.
     */
    public boolean canManageLeague(String leagueId) {
        return canManageScope(currentRoles(), ScopeType.LEAGUE, leagueId);
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
     * the league it hangs off. A {@code league_admin} of that league may too, for the same
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
            || (league != null && isLeagueAdmin(roles, league));
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

    /**
     * May edit/delete the given rule set. A blueprint: the admin of its owning region (or global). A
     * snapshot (docs/21): whoever manages the league or tier that owns it -- so a league admin may
     * tune their own league's rules.
     */
    public boolean canManageRuleSetById(String ruleSetId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        LeagueRuleSet ruleSet = ruleSetId == null ? null : leagueRuleSetRepository.findById(ruleSetId).orElse(null);
        if (ruleSet != null && ruleSet.isSnapshot()) {
            League owner = leagueRepository.findFirstByRuleSetId(ruleSetId).orElse(null);
            if (owner != null) {
                return canManageLeague(owner.getId());
            }
            Tier tierOwner = tierRepository.findFirstByRuleSetId(ruleSetId).orElse(null);
            return tierOwner != null && canManageTier(tierOwner.getId());
        }
        Federation federation = ruleSet == null ? null : ruleSet.getFederation();
        return federation != null && isRegionAdmin(roles, federation.getId());
    }

    /**
     * May overwrite the given leagues'/tiers' rules from a blueprint (docs/21): must manage every one of
     * them. Any blueprint may be used -- reading the library is open to every admin.
     */
    public boolean canApplyBlueprint(ApplyBlueprintRequest request) {
        if (request == null) {
            return false;
        }
        List<String> leagueIds = request.getLeagueIds() == null ? List.of() : request.getLeagueIds();
        List<String> tierIds = request.getTierIds() == null ? List.of() : request.getTierIds();
        return leagueIds.stream().allMatch(this::canManageLeague)
            && tierIds.stream().allMatch(this::canManageTier);
    }

    /**
     * May administer the given team participation (placement): it belongs to a league and thus to
     * that league's season and region, so the region's admin (or a global admin) manages it. This is
     * region placement authority - the same scope as editing the season itself (section 6 of the
     * model) - plus a {@code league_admin} of that participation's league, as a full league
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
            || (league != null && isLeagueAdmin(roles, league));
    }

    /**
     * May view the region's pending-roster-confirmation queue: the region's admin (or global),
     * who sees every league, OR a {@code league_admin} of at least one league in the region, who
     * sees just their own (see {@link #leagueAdminLeagueIdsInRegion}).
     */
    public boolean canViewPendingApprovals(String federationId) {
        return canManageRegion(federationId) || !leagueAdminLeagueIdsInRegion(federationId).isEmpty();
    }

    /**
     * League ids within {@code federationId} the caller is a {@code league_admin} of -- used to
     * scope the pending-approvals queue down to a league admin's own leagues when they aren't a
     * full region admin (who sees every league instead, unrestricted).
     */
    public List<String> leagueAdminLeagueIdsInRegion(String federationId) {
        if (federationId == null) {
            return List.of();
        }
        Set<String> leagueAdminScopeIds = currentRoles().stream()
            .filter(ra -> ra.getRole() == Role.LEAGUE_ADMIN)
            .map(RoleAssignment::getScopeId)
            .collect(Collectors.toSet());
        if (leagueAdminScopeIds.isEmpty()) {
            return List.of();
        }
        // every season-copy of the administered leagues (grants hold league identities, SPO-28), plus
        // any grant still holding a plain league row id -- same leniency as isLeagueAdmin
        Map<String, League> leagues = new LinkedHashMap<>();
        leagueRepository.findByLeagueIdentityIdIn(leagueAdminScopeIds).forEach(l -> leagues.put(l.getId(), l));
        leagueRepository.findAllById(leagueAdminScopeIds).forEach(l -> leagues.put(l.getId(), l));
        return leagues.values().stream()
            .filter(league -> league.getSeason() != null && league.getSeason().getFederation() != null
                && federationId.equals(league.getSeason().getFederation().getId()))
            .map(League::getId)
            .toList();
    }

    /**
     * May edit/submit the roster of the given participation (L2): the {@code team_admin} of the
     * participation's team, or an admin above it (club/region/global). Same authority as representing
     * the team in the result flow - see {@link #canReportMatchDay}. A {@code league_admin} of
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
        return canRepresent(roles, team) || (league != null && isLeagueAdmin(roles, league));
    }

    /**
     * May confirm/reopen the roster of the given participation (L2): an admin ABOVE the team
     * (club/region/global) - deliberately NOT the {@code team_admin} who submits, so the final say is
     * separate from the submission - or a {@code league_admin} of the participation's league,
     * as a full league admin.
     */
    public boolean canConfirmRoster(String participationId) {
        List<RoleAssignment> roles = currentRoles();
        TeamParticipation participation = participationId == null
            ? null : teamParticipationRepository.findById(participationId).orElse(null);
        Team team = participation == null ? null : participation.getTeam();
        League league = participation == null ? null : participation.getLeague();
        boolean adminAboveTeam = team != null && canManageScope(roles, ScopeType.TEAM, team.getTeamIdentityId());
        return adminAboveTeam || (league != null && isLeagueAdmin(roles, league));
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
    // admin above its league, OR a league_admin appointed to that league. Each entity resolves
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
     * {@code league_admin} appointed to that league. Distinct from {@link #canManageLeague}
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
        return regionAdmin || isLeagueAdmin(roles, league);
    }

    /**
     * Whether a {@code league_admin} grant covers this league. Grants point at the league identity
     * (SPO-28), so one grant covers every season-copy of the league; a grant still holding a row id
     * (before the identity existed, backfilled to equal the row id) matches the same way.
     */
    private boolean isLeagueAdmin(List<RoleAssignment> roles, League league) {
        return league != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.LEAGUE_ADMIN
                && (Objects.equals(ra.getScopeId(), league.getLeagueIdentityId())
                    || Objects.equals(ra.getScopeId(), league.getId())));
    }

    /**
     * Tier D: may propose/accept a date for the given match day on behalf of a participating team - a
     * {@code team_admin} of {@code teamHome}/{@code teamAway}, or an admin above that team
     * (club/region/global). The proposer-vs-accepter distinction is enforced in
     * {@code MatchDayService}. Results have their own gates: {@link #canEnterResult},
     * {@link #canConfirmResult} (docs/17).
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
     * May enter or edit the result of the fixture (docs/17): a team member of either side or a neutral
     * admin. The finer rules (ambiguous side, frozen result) are {@code MatchDayService}'s.
     */
    public boolean canEnterResult(String matchDayId) {
        ResultActor actor = resultActorFor(matchDayId);
        return actor != null && (actor.neutralAdmin() || actor.homeMember() || actor.awayMember());
    }

    /**
     * May confirm the result of the fixture (docs/17): a captain of either side or a neutral admin. The
     * same people enter line-ups and substitutions (docs/23); which side they may touch is checked in
     * {@code LineupService}.
     */
    public boolean canConfirmResult(String matchDayId) {
        ResultActor actor = resultActorFor(matchDayId);
        return actor != null && (actor.neutralAdmin() || actor.homeCaptain() || actor.awayCaptain());
    }

    private ResultActor resultActorFor(String matchDayId) {
        MatchDay matchDay = matchDayId == null ? null : matchDayRepository.findById(matchDayId).orElse(null);
        return matchDay == null ? null : resultActor(matchDay);
    }

    /**
     * What the current user is for this fixture's result (docs/17). Neutral admin: global admin,
     * admin of the league's federation, or league admin of the league -- not a club admin, who acts
     * as their team's side. Team member of a side: on its current roster in the league, its captain
     * ({@code team_admin}), or an admin above the team ({@link #canRepresent}). Captain: the
     * {@code team_admin} role only.
     */
    public ResultActor resultActor(MatchDay matchDay) {
        List<RoleAssignment> roles = currentRoles();
        League league = competitionResolver.ofMatchDay(matchDay.getId());
        boolean neutral = AccessRoles.isGlobalAdmin(roles) || (league != null && canOrganize(league));
        User user = registry.currentUser(currentJwt());
        Team home = matchDay.getTeamHome();
        Team away = matchDay.getTeamAway();
        boolean homeCaptain = isCaptain(roles, home);
        boolean awayCaptain = isCaptain(roles, away);
        return new ResultActor(neutral,
            homeCaptain || canRepresent(roles, home) || onRoster(user, home, league),
            awayCaptain || canRepresent(roles, away) || onRoster(user, away, league),
            homeCaptain, awayCaptain);
    }

    private boolean isCaptain(List<RoleAssignment> roles, Team team) {
        return team != null && roles.stream().anyMatch(ra ->
            ra.getRole() == Role.TEAM_ADMIN && Objects.equals(ra.getScopeId(), team.getTeamIdentityId()));
    }

    private boolean onRoster(User user, Team team, League league) {
        return user != null && team != null && league != null
            && rosterEntryRepository.isOnActiveRoster(user.getId(), team.getId(), league.getId());
    }

    /**
     * Whether the current player may act for {@code team}: its {@code team_admin}, or an admin above
     * it (club admin of its club, region admin of its region). Unlike {@link #canManageTeam} (team
     * CRUD - admins above only), this also accepts the {@code team_admin} role itself, which exists
     * precisely to act for one team in the result flow. Compared by {@link Team#getTeamIdentityId()},
     * not row id, so a role granted in one season keeps matching that team's copy in every other
     * season.
     */
    /**
     * May open the team area of {@code teamIdentityId}: its {@code team_admin}, or an admin above the
     * team (global, its region, its club -- {@link #canRepresent}). The area list itself stays limited
     * to explicit team grants (there are far too many teams); this admits a single team on demand.
     */
    public boolean canOpenTeamArea(String teamIdentityId) {
        List<RoleAssignment> roles = currentRoles();
        if (AccessRoles.isGlobalAdmin(roles)) {
            return true;
        }
        Team team = teamIdentityId == null ? null : teamService.latestForIdentity(teamIdentityId).orElse(null);
        return canRepresent(roles, team);
    }

    private boolean canRepresent(List<RoleAssignment> roles, Team team) {
        if (team == null) {
            return false;
        }
        boolean teamAdmin = roles.stream().anyMatch(ra ->
            ra.getRole() == Role.TEAM_ADMIN && Objects.equals(ra.getScopeId(), team.getTeamIdentityId()));
        return teamAdmin || canManageTeamRow(roles, team);
    }

    /**
     * Whether the caller administers {@code team} as "an admin above it" -- either via its club's
     * home region/club admin (the normal case), OR, root-federation reuse (docs/16-root-federation.md):
     * a region admin of the team's OWN season's federation, regardless of the club's home federation.
     * This is deliberately narrow -- it grants authority over THIS team only (and its roster), never
     * over the club's profile/membership or over any of that federation's own seasons/leagues/rule
     * sets, and it never walks the federation tree (it compares exactly two federations: the club's
     * home one and the team's own season's one), so it works identically at any tree depth.
     */
    private boolean canManageTeamRow(List<RoleAssignment> roles, Team team) {
        if (team == null) {
            return false;
        }
        Club club = team.getClub();
        boolean viaClubHomeRegion = club != null
            && (isRegionAdmin(roles, club.getFederationId()) || isClubAdmin(roles, club.getId()));
        Federation ownLeagueFederation = team.getSeason() == null ? null : team.getSeason().getFederation();
        boolean viaOwnLeagueFederation = ownLeagueFederation != null
            && isRegionAdmin(roles, ownLeagueFederation.getId());
        return viaClubHomeRegion || viaOwnLeagueFederation;
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
                // scopeId is the team's teamIdentityId (stable across every season-copy), not a row id --
                // any row sharing that identity resolves to the same club/region.
                Team team = scopeId == null ? null : teamService.latestForIdentity(scopeId).orElse(null);
                yield canManageTeamRow(roles, team);
            }
            case LEAGUE -> {
                // A league belongs to its region via League -> Season -> Federation; the region admin
                // of that region administers the league (e.g. to appoint a league admin) --
                // and, as a full league admin, a league_admin already appointed to it
                // administers it too (e.g. to appoint a co-admin), same pattern as a club admin
                // granting team_admin within their own club.
                // scopeId is a league row id or a league identity (what grants store, SPO-28).
                League league = leagueRepository.findByScopeId(scopeId).orElse(null);
                Federation region = league == null || league.getSeason() == null
                    ? null : league.getSeason().getFederation();
                yield (region != null && isRegionAdmin(roles, region.getId()))
                    || isLeagueAdmin(roles, league);
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
        User user = registry.currentUser(currentJwt());
        return roleAssignmentRepository.findByUser(user);
    }

    private Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("No authenticated JWT principal");
    }
}
