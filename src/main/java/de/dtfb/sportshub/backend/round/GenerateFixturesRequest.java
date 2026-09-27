package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
public class GenerateFixturesRequest {
    /** Reference date for round 1 — the first round's window start (WINDOW mode) or the initial
     * default date every generated fixture gets before an admin batches real dates in (DAY_BATCH
     * mode). Optional when {@link #slots} are given — the first slot is then the start. */
    private Instant startDate;

    /** Whether every pairing meets twice (home leg + mirrored away leg) instead of once. A
     * one-off choice at generation time, not a persisted ruleset setting. */
    private boolean doubleRoundRobin;

    /** Optional gap between rounds in DAY_BATCH mode (default 7 days) -- the provisional dates the
     * admin later refines. Not allowed in WINDOW mode (the rule set's window length is the gap)
     * nor together with {@link #slots}. */
    private Integer roundSpacingDays;

    /** Optional fixed slots (DAY_BATCH mode only): exactly one per generated round, in ascending
     * order. Round N gets slot N's date/time and venue, and its fixtures are CONFIRMED right away
     * — e.g. the Regionalliga weekend, Sat 10:00/13:00/15:30/18:00 + Sun 9:30/12:00/14:00. */
    private List<FixtureSlot> slots;
}
