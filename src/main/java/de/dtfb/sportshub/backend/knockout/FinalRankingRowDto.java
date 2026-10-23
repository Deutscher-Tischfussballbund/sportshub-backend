package de.dtfb.sportshub.backend.knockout;

import lombok.Getter;
import lombok.Setter;

/**
 * One team in a tier's final ranking (docs/34). {@code placeFrom}/{@code placeTo} are equal for a decided
 * place and span a shared range ("5–8") for teams that dropped out without a placement game; both null
 * while the team still plays for its place (or withdrew).
 */
@Getter
@Setter
public class FinalRankingRowDto {
    public enum Basis { KNOCKOUT_GAME, KNOCKOUT_ROUND, GROUP_PLACE }
    public enum Mark { PROMOTED, RELEGATED }

    private String teamId;
    private String teamName;
    private Integer placeFrom;
    private Integer placeTo;
    /** How the place was decided: a placing game, the knock-out round the team dropped out in, or its group place. */
    private Basis basis;
    /** The deciding fixture, round or group, for display. */
    private String detail;
    /** Not final yet: results still to come or not yet confirmed. */
    private boolean provisional;
    private boolean withdrawn;
    /** Within the tier's promotion / relegation places; null = neither (or a shared range crossing the line). */
    private Mark mark;
}
