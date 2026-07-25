package de.dtfb.sportshub.backend.league;

/**
 * Thrown when a hard delete is refused because the league still has a {@code Tier} or a
 * {@code TeamParticipation} referencing it directly (a groupless league can have participations
 * with no tier at all). Mapped to {@code 409 Conflict} -- delete the structure/participations
 * first, bottom-up.
 */
public class LeagueDeletionBlockedException extends RuntimeException {

    public LeagueDeletionBlockedException(String message) {
        super(message);
    }
}
