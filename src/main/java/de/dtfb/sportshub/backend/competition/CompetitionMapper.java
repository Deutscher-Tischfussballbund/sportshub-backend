package de.dtfb.sportshub.backend.competition;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CompetitionMapper {

    @Mapping(source = "season.id", target = "seasonId")
    CompetitionDto toDto(Competition event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "importId", ignore = true)
    Competition toEntity(CompetitionDto competitionDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "importId", ignore = true)
    void updateEntityFromDto(CompetitionDto dto, @MappingTarget Competition entity);

    List<CompetitionDto> toDtoList(List<Competition> events);
}
