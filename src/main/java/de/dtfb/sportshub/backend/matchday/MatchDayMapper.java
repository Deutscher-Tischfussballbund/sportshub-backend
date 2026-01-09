package de.dtfb.sportshub.backend.matchday;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchDayMapper {

    @Mapping(source = "round.id", target = "roundId")
    @Mapping(source = "teamAway.uuid", target = "teamAwayUuid")
    @Mapping(source = "teamHome.uuid", target = "teamHomeUuid")
    @Mapping(source = "location.uuid", target = "locationUuid")
    MatchDayDto toDto(MatchDay matchDay);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    MatchDay toEntity(MatchDayDto matchDayDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    void updateEntityFromDto(MatchDayDto dto, @MappingTarget MatchDay entity);

    List<MatchDayDto> toDtoList(List<MatchDay> matchDays);
}
