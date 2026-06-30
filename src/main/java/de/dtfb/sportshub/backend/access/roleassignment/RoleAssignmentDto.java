package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

public record RoleAssignmentDto(
    String id,
    Role role,
    ScopeType scopeType,
    String playerId,
    String scopeId,
    String grantedById,
    String createdAt
) {
}
