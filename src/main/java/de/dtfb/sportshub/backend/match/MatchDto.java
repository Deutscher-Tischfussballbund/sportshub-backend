package de.dtfb.sportshub.backend.match;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class MatchDto {
    private UUID uuid;
    private UUID matchdayUuid;
    private Instant startTime;
    private Instant endTime;
    private MatchState state;
    private MatchType type;
    private Integer homeScore;
    private Integer awayScore;
}
