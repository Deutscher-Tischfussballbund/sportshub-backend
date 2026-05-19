package de.dtfb.sportshub.backend.season;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    @Mapping(source = "federation.id", target = "federationId")
    SeasonDto toDto(Season season);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    Season toEntity(SeasonDto seasonDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    void updateEntityFromDto(SeasonDto dto, @MappingTarget Season entity);

    List<SeasonDto> toDtoList(List<Season> seasons);
}
