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
    @Mapping(target = "status", ignore = true) // starts ACTIVE; changed only via the withdraw endpoint
    @Mapping(target = "withdrawnAt", ignore = true)
    TeamParticipation toEntity(TeamParticipationDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "league", ignore = true)
    @Mapping(target = "group", ignore = true)
    @Mapping(target = "rosterStatus", ignore = true) // placement edits never change roster status
    @Mapping(target = "status", ignore = true) // placement edits never change withdrawal status
    @Mapping(target = "withdrawnAt", ignore = true)
    void updateEntityFromDto(TeamParticipationDto dto, @MappingTarget TeamParticipation entity);

    List<TeamParticipationDto> toDtoList(List<TeamParticipation> participations);
}
