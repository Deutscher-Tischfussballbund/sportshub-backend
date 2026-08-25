package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentDto;
import de.dtfb.sportshub.backend.user.UserDto;

import java.util.List;

public record MeResponseDto(
    UserDto user,
    List<RoleAssignmentDto> roles
) {
}
