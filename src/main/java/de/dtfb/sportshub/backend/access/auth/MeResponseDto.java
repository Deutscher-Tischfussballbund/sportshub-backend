package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentDto;
import de.dtfb.sportshub.backend.player.PlayerDto;

import java.util.List;

public record MeResponseDto(
    PlayerDto player,
    List<RoleAssignmentDto> roles
) {
}
