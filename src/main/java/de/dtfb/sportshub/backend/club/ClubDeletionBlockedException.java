package de.dtfb.sportshub.backend.club;

/**
 * Thrown when a hard delete is refused because the club still has teams and/or active members
 * attached to it. Mapped to {@code 409 Conflict} -- deactivate the club instead if it should no
 * longer be used.
 */
public class ClubDeletionBlockedException extends RuntimeException {

    public ClubDeletionBlockedException(String message) {
        super(message);
    }
}
