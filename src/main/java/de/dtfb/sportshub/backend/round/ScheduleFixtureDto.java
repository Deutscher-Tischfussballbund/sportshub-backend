package de.dtfb.sportshub.backend.round;

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
}
