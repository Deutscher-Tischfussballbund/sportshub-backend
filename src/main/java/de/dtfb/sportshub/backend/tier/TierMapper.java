package de.dtfb.sportshub.backend.tier;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TierMapper {

    @Mapping(source = "league.id", target = "leagueId")
    @Mapping(source = "ruleSet.id", target = "ruleSetId")
    TierDto toDto(Tier tier);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "league", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    Tier toEntity(TierDto tierDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "league", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    void updateEntityFromDto(TierDto dto, @MappingTarget Tier entity);

    List<TierDto> toDtoList(List<Tier> tiers);
}
