package de.dtfb.sportshub.backend.matchday;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MatchDayResultRequest {
    private List<MatchResultEntry> matches;

    @Getter
    @Setter
    public static class MatchResultEntry {
        private String matchId;
        private Integer homeScore;
        private Integer awayScore;
    }
}
