package de.dtfb.sportshub.backend.matchday;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchdayMapper {

    @Mapping(source = "round.uuid", target = "roundUuid")
    @Mapping(source = "teamAway.uuid", target = "teamAwayUuid")
    @Mapping(source = "teamHome.uuid", target = "teamHomeUuid")
    @Mapping(source = "location.uuid", target = "locationUuid")
    MatchdayDto toDto(Matchday matchday);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    Matchday toEntity(MatchdayDto matchdayDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    void updateEntityFromDto(MatchdayDto dto, @MappingTarget Matchday entity);

    List<MatchdayDto> toDtoList(List<Matchday> matchdays);
}
