package de.dtfb.sportshub.backend.standing;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StandingDto {
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
    /** Live table only: the row counts at least one entered but not yet confirmed fixture. */
    private boolean provisional;
}
