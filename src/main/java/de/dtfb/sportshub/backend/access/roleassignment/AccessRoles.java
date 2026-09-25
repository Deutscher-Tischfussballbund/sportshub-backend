package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Small shared predicates over a player's role assignments. */
public final class AccessRoles {

    private AccessRoles() {
    }

    /** Org-administration and league roles -- everything except {@code team_admin} and the deprecated uploaders. */
    private static final Set<Role> ADMIN_ROLES =
        EnumSet.of(Role.ADMIN, Role.REGION_ADMIN, Role.CLUB_ADMIN, Role.LEAGUE_ADMIN);

    public static boolean hasAnyAdminRole(List<RoleAssignment> roles) {
        return roles.stream().anyMatch(ra -> ADMIN_ROLES.contains(ra.getRole()));
    }

    public static boolean isGlobalAdmin(List<RoleAssignment> roles) {
        return roles.stream().anyMatch(ra -> ra.getRole() == Role.ADMIN && ra.getScopeType() == ScopeType.GLOBAL);
    }
}
