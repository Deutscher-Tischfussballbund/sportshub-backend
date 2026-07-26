package de.dtfb.sportshub.backend.round;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class GenerateFixturesRequest {
    /** Reference date for round 1 — the first round's window start (WINDOW mode) or the initial
     * default date every generated fixture gets before an admin batches real dates in (DAY_BATCH
     * mode). */
    private Instant startDate;

    /** Whether every pairing meets twice (home leg + mirrored away leg) instead of once. A
     * one-off choice at generation time, not a persisted ruleset setting. */
    private boolean doubleRoundRobin;
}
