package de.dtfb.sportshub.backend.teamparticipation;

/**
 * Thrown when a hard delete is refused because the team already has recorded {@code MatchDay}s or
 * a {@code Standing} row in this league. Mapped to {@code 409 Conflict} so the frontend can explain
 * the block and offer to withdraw instead (which preserves the participation and its history).
 */
public class ParticipationDeletionBlockedException extends RuntimeException {

    public ParticipationDeletionBlockedException(String message) {
        super(message);
    }
}
