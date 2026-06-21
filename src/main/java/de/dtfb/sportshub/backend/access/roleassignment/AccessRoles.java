package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

import java.util.List;

/** Small shared predicates over a player's role assignments. */
public final class AccessRoles {

    private AccessRoles() {
    }

    public static boolean isGlobalAdmin(List<RoleAssignment> roles) {
        return roles.stream().anyMatch(ra -> ra.getRole() == Role.ADMIN && ra.getScopeType() == ScopeType.GLOBAL);
    }
}
