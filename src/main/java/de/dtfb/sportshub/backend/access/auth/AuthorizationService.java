package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.AccessRoles;
import de.dtfb.sportshub.backend.access.roleassignment.GrantRoleDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerRegistryService;
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

    public AuthorizationService(PlayerRegistryService registry,
                                RoleAssignmentRepository roleAssignmentRepository,
                                ClubRepository clubRepository,
                                TeamRepository teamRepository) {
        this.registry = registry;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.clubRepository = clubRepository;
        this.teamRepository = teamRepository;
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
