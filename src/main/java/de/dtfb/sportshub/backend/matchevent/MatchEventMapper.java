package de.dtfb.sportshub.backend.matchevent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.*;

import java.io.IOException;
import java.util.List;

@SuppressWarnings("unused")
@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchEventMapper {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(source = "match.id", target = "matchId")
    @Mapping(source = "team.id", target = "teamId")
    @Mapping(target = "json", expression = "java(fromJsonString(matchEvent.getJson()))")
    MatchEventDto toDto(MatchEvent matchEvent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "json", expression = "java(toJsonString(matchEventDto.getJson()))")
    MatchEvent toEntity(MatchEventDto matchEventDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "team", ignore = true)
    @Mapping(target = "json", ignore = true) // handled by updateJsonAfterMapping
    void updateEntityFromDto(MatchEventDto dto, @MappingTarget MatchEvent entity);

    List<MatchEventDto> toDtoList(List<MatchEvent> matchEvents);

    // @Named so MapStruct does NOT adopt these as implicit String<->Object converters for every
    // String field (which would JSON-quote id/matchId/teamId/playerId). Only the json mapping,
    // which references them explicitly via expression(), uses them.
    @Named("fromJsonString")
    default Object fromJsonString(String json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, Object.class);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON stored in DB", e);
        }
    }

    @Named("toJsonString")
    default String toJsonString(Object json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(json);
        } catch (IOException e) {
            throw new IllegalStateException("Invalid JSON stored in DB", e);
        }
    }

    @AfterMapping
    default void updateJsonAfterMapping(MatchEventDto dto, @MappingTarget MatchEvent entity) {
        if (dto.getJson() != null) {
            entity.setJson(toJsonString(dto.getJson()));
        }
        // if dto.getJson() is null, do nothing → preserves existing JSON
    }
}
