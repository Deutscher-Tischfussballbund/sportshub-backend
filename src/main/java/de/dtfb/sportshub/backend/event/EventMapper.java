package de.dtfb.sportshub.backend.event;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EventMapper {

    @Mapping(source = "season.uuid", target = "seasonUuid")
    EventDto toDto(Event event);

    @Mapping(target = "id", ignore = true)
    Event toEntity(EventDto eventDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(EventDto dto, @MappingTarget Event entity);

    List<EventDto> toDtoList(List<Event> events);
}
