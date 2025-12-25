package de.dtfb.sportshub.backend.season;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SeasonMapper {

    SeasonDto toDto(Season team);

    @Mapping(target = "id", ignore = true)
    Season toEntity(SeasonDto teamDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(SeasonDto dto, @MappingTarget Season entity);

    List<SeasonDto> toDtoList(List<Season> teams);
}
