package de.dtfb.sportshub.backend.matchset;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MatchSetDto {
    private String id;
    private String matchId;
    private Integer setNumber;
    private Integer homeScore;
    private Integer awayScore;
}
