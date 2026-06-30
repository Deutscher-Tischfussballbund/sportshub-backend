package de.dtfb.sportshub.backend.matchevent;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@NoArgsConstructor
@Data
public class MatchEventDto {
    private String id;
    private String playerId;
    private String matchId;
    private String teamId;
    private Instant timestamp;
    private Integer homeScore;
    private Integer awayScore;
    private MatchEventType type;

    /** Arbitrary event payload; stored as a JSON string, exposed as parsed JSON. */
    private Object json;
}
