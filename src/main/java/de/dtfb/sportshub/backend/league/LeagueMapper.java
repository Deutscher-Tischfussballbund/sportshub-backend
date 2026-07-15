package de.dtfb.sportshub.backend.league;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeagueMapper {

    @Mapping(source = "season.id", target = "seasonId")
    @Mapping(source = "category.id", target = "categoryId")
    @Mapping(source = "ruleSet.id", target = "ruleSetId")
    LeagueDto toDto(League league);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    @Mapping(target = "importId", ignore = true)
    League toEntity(LeagueDto leagueDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    @Mapping(target = "category", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    @Mapping(target = "importId", ignore = true)
    void updateEntityFromDto(LeagueDto dto, @MappingTarget League entity);

    List<LeagueDto> toDtoList(List<League> leagues);
}
