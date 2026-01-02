package de.dtfb.sportshub.backend.phase;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PhaseMapper {

    @Mapping(source = "stage.uuid", target = "stageUuid")
    PhaseDto toDto(Phase phase);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "stageUuid", target = "stage.uuid")
    Phase toEntity(PhaseDto phaseDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "stage", ignore = true)
    void updateEntityFromDto(PhaseDto dto, @MappingTarget Phase entity);

    List<PhaseDto> toDtoList(List<Phase> phases);
}
