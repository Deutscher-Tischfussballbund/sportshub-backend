package de.dtfb.sportshub.backend.matchday;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchDayMapper {

    @Mapping(source = "round.id", target = "roundId")
    @Mapping(source = "teamAway.id", target = "teamAwayId")
    @Mapping(source = "teamHome.id", target = "teamHomeId")
    @Mapping(source = "location.id", target = "locationId")
    MatchDayDto toDto(MatchDay matchDay);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    MatchDay toEntity(MatchDayDto matchDayDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "round", ignore = true)
    @Mapping(target = "location", ignore = true)
    @Mapping(target = "teamHome", ignore = true)
    @Mapping(target = "teamAway", ignore = true)
    void updateEntityFromDto(MatchDayDto dto, @MappingTarget MatchDay entity);

    List<MatchDayDto> toDtoList(List<MatchDay> matchDays);
}
