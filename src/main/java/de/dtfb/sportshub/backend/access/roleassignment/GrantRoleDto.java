package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;

public record GrantRoleDto(
    String playerId,
    Role role,
    String scopeId
) {
}
