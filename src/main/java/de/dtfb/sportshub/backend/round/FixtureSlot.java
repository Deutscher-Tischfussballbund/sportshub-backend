package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/** One fixed kick-off slot of a tournament-style schedule: round N of the generated plan is played
 * at slot N — every fixture of that round starts at {@code startDate} at {@code location}.
 * See docs/12-matchday-scheduling.md §2. */
@Getter
@Setter
public class FixtureSlot {
    private Instant startDate;

    /** The venue of this slot (usually the host of the tournament day); optional. */
    private String locationId;
}
