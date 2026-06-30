package de.dtfb.sportshub.backend.access.area;

import java.util.List;

public record MeAreasResponseDto(
    List<AreaDto> areas
) {
}
