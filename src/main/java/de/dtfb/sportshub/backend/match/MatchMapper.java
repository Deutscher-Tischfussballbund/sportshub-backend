package de.dtfb.sportshub.backend.match;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchMapper {

    @Mapping(source = "matchday.uuid", target = "matchdayUuid")
    MatchDto toDto(Match match);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "matchday", ignore = true)
    Match toEntity(MatchDto matchDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "matchday", ignore = true)
    void updateEntityFromDto(MatchDto dto, @MappingTarget Match entity);

    List<MatchDto> toDtoList(List<Match> matches);
}
