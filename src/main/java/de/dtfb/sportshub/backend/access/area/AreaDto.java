package de.dtfb.sportshub.backend.access.area;

public record AreaDto(
    String type,
    String id,
    String name,
    String regionId,
    String regionName
) {
}
