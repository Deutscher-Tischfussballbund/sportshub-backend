package de.dtfb.sportshub.backend.teamparticipation;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TeamParticipationMapper {

    @Mapping(source = "team.id", target = "teamId")
    @Mapping(source = "competition.id", target = "competitionId")
    @Mapping(source = "competition.season.id", target = "seasonId")
    @Mapping(source = "pool.id", target = "poolId")
    TeamParticipationDto toDto(TeamParticipation participation);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "competition", ignore = true)
    @Mapping(target = "pool", ignore = true)
    TeamParticipation toEntity(TeamParticipationDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "competition", ignore = true)
    @Mapping(target = "pool", ignore = true)
    void updateEntityFromDto(TeamParticipationDto dto, @MappingTarget TeamParticipation entity);

    List<TeamParticipationDto> toDtoList(List<TeamParticipation> participations);
}
