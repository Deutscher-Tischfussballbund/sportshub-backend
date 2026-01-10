package de.dtfb.sportshub.backend.matchevent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.io.IOException;
import java.util.List;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface MatchEventMapper {

    ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mapping(source = "match.id", target = "matchId")
    @Mapping(source = "team.id", target = "teamId")
    MatchEventDto toDto(MatchEvent matchEvent);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "team", ignore = true)
    MatchEvent toEntity(MatchEventDto matchEventDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "match", ignore = true)
    @Mapping(target = "team", ignore = true)
    void updateEntityFromDto(MatchEventDto dto, @MappingTarget MatchEvent entity);

    List<MatchEventDto> toDtoList(List<MatchEvent> matchEvents);

    default String map(JsonNode node) {
        return node == null ? null : node.toString();
    }

    default JsonNode map(String json) {
        try {
            return json == null ? null : OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON stored in DB", e);
        }
    }
}
