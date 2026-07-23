package de.dtfb.sportshub.backend.teamparticipation;

import lombok.Getter;

/**
 * Thrown when a team tries to register (create a {@link TeamParticipation}) for a league whose
 * season has already ended. Registering a NEW participation had no season-state check at all
 * before this (only authorization) — the registration window ({@code registrationOpen}) is a
 * distinct concept from the season's own {@code endDate}, so this checks {@code endDate} directly
 * rather than assuming a closed registration window means the season is over. Mapped to 409 with a
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
