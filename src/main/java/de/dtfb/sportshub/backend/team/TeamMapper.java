package de.dtfb.sportshub.backend.team;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    TeamDto toDto(Team team);

    @Mapping(target = "id", ignore = true)
    Team toEntity(TeamDto teamDto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(TeamDto dto, @MappingTarget Team entity);

    List<TeamDto> toDtoList(List<Team> teams);
}
