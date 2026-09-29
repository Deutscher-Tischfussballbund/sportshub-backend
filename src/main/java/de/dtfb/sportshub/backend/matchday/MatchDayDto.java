package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.lineup.LineupStatus;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class MatchDayDto {
    private String id;
    private String name;
    private String roundId;
    private String locationId;
    private String teamAwayId;
    private String teamHomeId;
    /** Read-only: the teams' identities (stable across seasons) -- the team area matches fixtures by them. */
    private String teamHomeIdentityId;
    private String teamAwayIdentityId;
    /** Read-only: the teams' names, so lists don't have to resolve every team row. */
    private String teamHomeName;
    private String teamAwayName;
    private Instant startDate;
    private Instant endDate;
    private ResultState resultState;
    private Instant homeConfirmedAt;
    private Instant awayConfirmedAt;
    /** Read-only: the fixture against the bye (no away team; scored with the rule set's bye score). */
    private boolean bye;
    /** Read-only: the score as lists show it (docs/22) -- race: running score; games: games won; bye: bye score. Null = nothing entered. */
    private Integer scoreHome;
    private Integer scoreAway;
    /** Read-only: games (segments) entered / total, for "(3/7)". */
    private int gamesEntered;
    private int gamesTotal;
    /** Read-only (docs/23): the rule set requires line-ups before result entry, and each side's line-up status. */
    private boolean lineupRequired;
    private LineupStatus lineupHome;
    private LineupStatus lineupAway;
    private SchedulingState schedulingState;
    private String scheduleProposedByDtfbId;
    private Instant scheduleConfirmedAt;
}
