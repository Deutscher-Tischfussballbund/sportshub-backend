package de.dtfb.sportshub.backend.team;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamMapper {

    @Mapping(source = "club.id", target = "clubId")
    @Mapping(source = "season.id", target = "seasonId")
    @Mapping(target = "clubName", ignore = true) // set by TeamService, resolved as of the team's season
    TeamDto toDto(Team team);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "club", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "teamIdentityId", ignore = true)
    @Mapping(target = "copiedFromTeamId", ignore = true)
    Team toEntity(TeamDto teamDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "club", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "teamIdentityId", ignore = true)
    @Mapping(target = "copiedFromTeamId", ignore = true)
    void updateEntityFromDto(TeamDto dto, @MappingTarget Team entity);

    List<TeamDto> toDtoList(List<Team> teams);
}
