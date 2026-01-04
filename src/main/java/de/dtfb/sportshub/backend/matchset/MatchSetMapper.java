package de.dtfb.sportshub.backend.matchset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchSetMapper {

    @Mapping(source = "match.uuid", target = "matchUuid")
    MatchSetDto toDto(MatchSet matchSet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    MatchSet toEntity(MatchSetDto matchSetDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "match", ignore = true)
    void updateEntityFromDto(MatchSetDto dto, @MappingTarget MatchSet entity);

    List<MatchSetDto> toDtoList(List<MatchSet> matchSets);
}
