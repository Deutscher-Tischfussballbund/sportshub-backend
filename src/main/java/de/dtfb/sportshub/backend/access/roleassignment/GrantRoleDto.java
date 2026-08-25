package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;

public record GrantRoleDto(
    String userId,
    Role role,
    String scopeId
) {
}
