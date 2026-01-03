package de.dtfb.sportshub.backend.discipline;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface DisciplineMapper {

    @Mapping(source = "event.uuid", target = "eventUuid")
    DisciplineDto toDto(Discipline discipline);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "event", ignore = true)
    Discipline toEntity(DisciplineDto disciplineDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "event", ignore = true)
    void updateEntityFromDto(DisciplineDto dto, @MappingTarget Discipline entity);

    List<DisciplineDto> toDtoList(List<Discipline> disciplines);
}
