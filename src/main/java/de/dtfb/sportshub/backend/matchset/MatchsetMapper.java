package de.dtfb.sportshub.backend.matchset;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchsetMapper {

    @Mapping(source = "match.uuid", target = "matchUuid")
    MatchsetDto toDto(Matchset matchset);

    @Mapping(target = "id", ignore = true)
    Matchset toEntity(MatchsetDto matchsetDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(MatchsetDto dto, @MappingTarget Matchset entity);

    List<MatchsetDto> toDtoList(List<Matchset> matchsets);
}
