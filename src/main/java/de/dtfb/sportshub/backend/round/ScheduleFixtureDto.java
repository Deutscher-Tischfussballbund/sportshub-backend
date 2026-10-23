package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.matchday.FixtureSourceDto;
import de.dtfb.sportshub.backend.lineup.LineupStatus;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One fixture (MatchDay) of a {@link ScheduleRoundDto}, with team and venue names resolved. */
@Getter
@Setter
public class ScheduleFixtureDto {
    private String id;
    /** Label of a knock-out fixture ("HF 1", "Finale", "Spiel um Platz 3"); null for a league fixture. */
    private String name;
    /** Knock-out stage (docs/34): where each side's team comes from, shown while it isn't filled. */
    private FixtureSourceDto homeSource;
    private FixtureSourceDto awaySource;
    private boolean homeSetByHand;
    private boolean awaySetByHand;
    private Integer winnerPlace;
    private Integer loserPlace;
    private String teamHomeId;
    private String teamHomeName;
    private String teamAwayId;
    private String teamAwayName;
    private Instant startDate;
    private String locationId;
    private String locationName;
    private SchedulingState schedulingState;
    private ResultState resultState;
    /** The fixture against the bye (no away team; scored with the rule set's bye score, docs/22). */
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
}
