package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.club.ClubDto;
import de.dtfb.sportshub.backend.federation.FederationDto;

import java.util.List;

public record GrantableScopesDto(
    boolean canGrantAdmin,
    List<FederationDto> regions,
    List<ClubDto> clubs
) {
}
