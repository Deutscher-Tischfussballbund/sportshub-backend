package de.dtfb.sportshub.backend.club;

public record ClubDto(
    String id,
    String name,
    String shortName,
    String city,
    boolean active,
    String regionId // value is the club's federationId (region == federation)
) {
}
