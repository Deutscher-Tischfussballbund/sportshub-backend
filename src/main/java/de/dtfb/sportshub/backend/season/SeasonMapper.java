package de.dtfb.sportshub.backend.season;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    SeasonDto toDto(Season season);

    @Mapping(target = "id", ignore = true)
    Season toEntity(SeasonDto seasonDto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(SeasonDto dto, @MappingTarget Season entity);

    List<SeasonDto> toDtoList(List<Season> seasons);
}
