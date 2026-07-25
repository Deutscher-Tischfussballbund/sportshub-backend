package de.dtfb.sportshub.backend.tier;

/**
 * Thrown when a hard delete is refused because the tier still has a {@code Group} underneath it.
 * Mapped to {@code 409 Conflict} -- delete the groups first.
 */
public class TierDeletionBlockedException extends RuntimeException {

    public TierDeletionBlockedException(String message) {
        super(message);
    }
}
