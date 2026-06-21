package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

import de.dtfb.sportshub.backend.player.PlayerDto;

public record RoleAssignmentViewDto(
    String id,
    Role role,
    ScopeType scopeType,
    String scopeId,
    String scopeName,
    PlayerDto player,
    String grantedByName,
    String createdAt
) {
}
