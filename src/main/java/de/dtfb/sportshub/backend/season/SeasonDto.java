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

    /** Registration window. {@code registrationOpensAt} is required for registration to ever be
     * open; {@code registrationClosesAt} is optional (unset = stays open indefinitely once started). */
    private LocalDate registrationOpensAt;
    private LocalDate registrationClosesAt;

    /** Whether team registration is open right now. Read-only: derived fresh from the window above
     * on every read, not settable directly. */
    private boolean registrationOpen;

    /** Set when the season is archived (soft-deleted); null while active. Read-only. */
    private Instant archivedAt;
}
