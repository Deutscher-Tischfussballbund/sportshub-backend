package de.dtfb.sportshub.backend.team;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    @Mapping(source = "uuid", target = "id")
    TeamDto toDto(Team team);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "id", target = "uuid")
    Team toEntity(TeamDto teamDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "id", target = "uuid")
    void updateEntityFromDto(TeamDto dto, @MappingTarget Team entity);

    List<TeamDto> toDtoList(List<Team> teams);

    List<Team> toEntityList(List<TeamDto> dtos);
}
