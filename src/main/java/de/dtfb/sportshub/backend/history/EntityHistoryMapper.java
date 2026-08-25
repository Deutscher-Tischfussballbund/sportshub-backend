package de.dtfb.sportshub.backend.history;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface EntityHistoryMapper {

    EntityHistoryDto toDto(EntityHistoryEntry entry);

    List<EntityHistoryDto> toDtoList(List<EntityHistoryEntry> entries);
}
