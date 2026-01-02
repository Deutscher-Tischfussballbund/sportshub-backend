package de.dtfb.sportshub.backend.stage;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface StageMapper {

    @Mapping(source = "discipline.uuid", target = "disciplineUuid")
    StageDto toDto(Stage stage);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "disciplineUuid", target = "discipline.uuid")
    Stage toEntity(StageDto stageDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "discipline", ignore = true)
    void updateEntityFromDto(StageDto dto, @MappingTarget Stage entity);

    List<StageDto> toDtoList(List<Stage> stages);
}
