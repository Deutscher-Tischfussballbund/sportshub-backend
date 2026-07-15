package de.dtfb.sportshub.backend.leaguerules;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeagueRuleSetMapper {

    @Mapping(source = "federation.id", target = "federationId")
    // gamePlan is held as separate GamePlanEntry rows; the service assembles it.
    @Mapping(target = "gamePlan", ignore = true)
    LeagueRuleSetDto toDto(LeagueRuleSet ruleSet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    LeagueRuleSet toEntity(LeagueRuleSetDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    void updateEntityFromDto(LeagueRuleSetDto dto, @MappingTarget LeagueRuleSet entity);

    List<LeagueRuleSetDto> toDtoList(List<LeagueRuleSet> ruleSets);

    GamePlanEntryDto toGamePlanDto(GamePlanEntry entry);

    List<GamePlanEntryDto> toGamePlanDtoList(List<GamePlanEntry> entries);
}
