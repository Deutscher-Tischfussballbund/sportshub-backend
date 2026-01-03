package de.dtfb.sportshub.backend.round;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface RoundMapper {

    @Mapping(source = "pool.uuid", target = "poolUuid")
    RoundDto toDto(Round round);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "pool", ignore = true)
    Round toEntity(RoundDto roundDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "pool", ignore = true)
    void updateEntityFromDto(RoundDto dto, @MappingTarget Round entity);

    List<RoundDto> toDtoList(List<Round> rounds);
}
