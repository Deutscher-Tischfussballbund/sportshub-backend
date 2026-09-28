package de.dtfb.sportshub.backend.leaguerules;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LeagueRuleSetMapper {

    @Mapping(source = "federation.id", target = "federationId")
    @Mapping(source = "sourceBlueprint.id", target = "sourceBlueprintId")
    // gamePlan is held as separate GamePlanEntry rows; the service assembles it. frozen needs the
    // owner's season, gamePlanLocked the owner's fixtures -- both resolved by the service.
    @Mapping(target = "gamePlan", ignore = true)
    @Mapping(target = "frozen", ignore = true)
    @Mapping(target = "gamePlanLocked", ignore = true)
    LeagueRuleSetDto toDto(LeagueRuleSet ruleSet);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    @Mapping(target = "snapshot", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "sourceBlueprint", ignore = true)
    LeagueRuleSet toEntity(LeagueRuleSetDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    @Mapping(target = "snapshot", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "sourceBlueprint", ignore = true)
    void updateEntityFromDto(LeagueRuleSetDto dto, @MappingTarget LeagueRuleSet entity);

    /** Copies the rule fields (and name) only -- identity, owner and role stay with the target. */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "federation", ignore = true)
    @Mapping(target = "snapshot", ignore = true)
    @Mapping(target = "archived", ignore = true)
    @Mapping(target = "sourceBlueprint", ignore = true)
    void copyRules(LeagueRuleSet source, @MappingTarget LeagueRuleSet target);

    List<LeagueRuleSetDto> toDtoList(List<LeagueRuleSet> ruleSets);

    GamePlanEntryDto toGamePlanDto(GamePlanEntry entry);

    List<GamePlanEntryDto> toGamePlanDtoList(List<GamePlanEntry> entries);
}
