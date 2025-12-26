package de.dtfb.sportshub.backend.event;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(source = "season.uuid", target = "seasonUuid")
    EventDto toDto(Event team);

    @Mapping(target = "id", ignore = true)
    @Mapping(source = "seasonUuid", target = "season.uuid")
    Event toEntity(EventDto teamDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "season", ignore = true)
    void updateEntityFromDto(EventDto dto, @MappingTarget Event entity);

    List<EventDto> toDtoList(List<Event> teams);
}
