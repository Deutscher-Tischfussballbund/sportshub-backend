package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;

import de.dtfb.sportshub.backend.user.UserDto;

public record RoleAssignmentViewDto(
    String id,
    Role role,
    ScopeType scopeType,
    String scopeId,
    String scopeName,
    UserDto user,
    String grantedByName,
    String createdAt
) {
}
