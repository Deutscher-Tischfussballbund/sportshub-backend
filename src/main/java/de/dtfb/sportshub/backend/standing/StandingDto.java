package de.dtfb.sportshub.backend.standing;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StandingDto {
    /** 1-based place in the ranked table (docs/22 order). */
    private int place;
    private String teamId;
    private String teamName;
    private int played;
    private int wins;
    private int draws;
    private int losses;
    private int points;
    private int setsWon;
    private int setsLost;
    private int setDifference;
    /** Goals for/against and their difference -- in a RACE league the running scores; the same numbers as the set fields. */
    private int goalsFor;
    private int goalsAgainst;
    private int goalDifference;
    /** Live table only: the row counts at least one entered but not yet confirmed fixture. */
    private boolean provisional;
}
