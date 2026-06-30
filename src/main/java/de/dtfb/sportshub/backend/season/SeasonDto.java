package de.dtfb.sportshub.backend.season;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
public class SeasonDto {
    private String id;
    private String name;
    private String federationId;

    // General duration.
    private LocalDate startDate;
    private LocalDate endDate;

    /** Whether team registration is open for this season. */
    private boolean registrationOpen;

    /** Set when the season is archived (soft-deleted); null while active. Read-only. */
    private Instant archivedAt;
}
