package de.dtfb.sportshub.backend.leaguerules;

/**
 * Thrown when a hard delete is refused because the rule set is still referenced by a
 * {@code League}, a {@code Tier}, or a {@code Federation}'s default rule set. Mapped to
 * {@code 409 Conflict} -- detach the reference(s) first.
 */
public class RuleSetDeletionBlockedException extends RuntimeException {

    public RuleSetDeletionBlockedException(String message) {
        super(message);
    }
}
