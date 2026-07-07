package de.dtfb.sportshub.backend.teamparticipation;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamParticipationMapper {

    @Mapping(source = "team.id", target = "teamId")
    @Mapping(source = "league.id", target = "leagueId")
    @Mapping(source = "league.season.id", target = "seasonId")
    @Mapping(source = "group.id", target = "groupId")
    TeamParticipationDto toDto(TeamParticipation participation);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "league", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "rosterStatus", ignore = true) // starts DRAFT; changed only via roster lifecycle
    TeamParticipation toEntity(TeamParticipationDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "league", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "rosterStatus", ignore = true) // placement edits never change roster status
    void updateEntityFromDto(TeamParticipationDto dto, @MappingTarget TeamParticipation entity);

    List<TeamParticipationDto> toDtoList(List<TeamParticipation> participations);
}
