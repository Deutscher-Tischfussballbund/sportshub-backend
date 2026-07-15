package de.dtfb.sportshub.backend.leaguerules;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class LeagueRuleSetDto {
    private String id;
    private String name;
    /** Owning region; null = DTFB-global template. */
    private String federationId;

    private PlaySystem playSystem;

    private Integer pointsWin;
    private Integer pointsDraw;
    private Integer pointsLoss;

    private Integer setsPerGame;
    private Integer pointsToWinSet;

    private MatchdayDecision matchdayDecision;
    private Integer matchdayTarget;

    private Boolean sideSwitchAllowed;

    /** Ordered matchday composition; replaced wholesale on update. */
    private List<GamePlanEntryDto> gamePlan;
}
