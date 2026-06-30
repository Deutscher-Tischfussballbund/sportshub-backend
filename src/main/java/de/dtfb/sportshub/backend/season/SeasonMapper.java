package de.dtfb.sportshub.backend.season;

import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    @Mapping(source = "federation.id", target = "federationId")
    @Mapping(target = "open", ignore = true) // derived in computeOpen
    SeasonDto toDto(Season season);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    Season toEntity(SeasonDto seasonDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    void updateEntityFromDto(SeasonDto dto, @MappingTarget Season entity);

    List<SeasonDto> toDtoList(List<Season> seasons);

    /**
     * Derives {@code isOpen}: registration is on AND now is within the (optional) window.
     * A missing bound means "unbounded" on that side.
     */
    @AfterMapping
    default void computeOpen(Season season, @MappingTarget SeasonDto dto) {
        Instant now = Instant.now();
        Instant opensAt = season.getRegistrationOpensAt();
        Instant closesAt = season.getRegistrationClosesAt();
        boolean afterStart = opensAt == null || !now.isBefore(opensAt);
        boolean beforeEnd = closesAt == null || !now.isAfter(closesAt);
        dto.setOpen(season.isRegistrationOpen() && afterStart && beforeEnd);
    }
}
