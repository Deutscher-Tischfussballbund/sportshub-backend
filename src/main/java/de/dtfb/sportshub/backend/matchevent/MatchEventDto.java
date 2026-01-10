package de.dtfb.sportshub.backend.matchevent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@NoArgsConstructor
@Data
public class MatchEventDto {
    private UUID id;
    private UUID playerId;
    private UUID matchId;
    private UUID teamId;
    private Instant timestamp;
    private Integer homeScore;
    private Integer awayScore;
    private MatchEventType type;

    private JsonNode json;
}
