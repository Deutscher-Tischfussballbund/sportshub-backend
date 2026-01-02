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
    MatchdayDto toDto(Matchday round);

    @Mapping(target = "id", ignore = true)
    Matchday toEntity(MatchdayDto roundDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(MatchdayDto dto, @MappingTarget Matchday entity);

    List<MatchdayDto> toDtoList(List<Matchday> rounds);
}
