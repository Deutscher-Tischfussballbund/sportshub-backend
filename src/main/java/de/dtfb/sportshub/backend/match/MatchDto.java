package de.dtfb.sportshub.backend.match;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MatchDto {
    private String id;
    private String matchDayId;
    /** Order of this game within its matchday (1-based); maps to GamePlanEntry.position. */
    private Integer position;
    private Instant startTime;
    private Instant endTime;
    private MatchState state;
    private MatchType type;
    private Integer homeScore;
    private Integer awayScore;
    private Winner winner;
}
