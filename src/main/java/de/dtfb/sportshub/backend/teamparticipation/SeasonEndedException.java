package de.dtfb.sportshub.backend.teamparticipation;

import lombok.Getter;

/**
 * Thrown when a team tries to register (create a {@link TeamParticipation}) for a league whose
 * season has already ended. Registering a NEW participation had no season-state check at all
 * before this (only authorization) — {@code registrationOpen} is a manually-toggled flag that can
 * go stale, so this checks the season's actual {@code endDate} instead. Mapped to 409 with a
 * structured body (code + endDate) — see {@link SeasonEndedError}.
 */
@Getter
public class SeasonEndedException extends RuntimeException {

    private final String endDate;

    public SeasonEndedException(String message, String endDate) {
        super(message);
        this.endDate = endDate;
    }
}
