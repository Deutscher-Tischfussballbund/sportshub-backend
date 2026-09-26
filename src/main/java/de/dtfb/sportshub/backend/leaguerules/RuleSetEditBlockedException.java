package de.dtfb.sportshub.backend.leaguerules;

/**
 * Thrown when a change would alter the rules of a season that has already ended -- editing a
 * league's/tier's snapshot, resetting it from a blueprint, or adding/removing a tier override
 * (docs/21-rule-set-blueprints.md). Mapped to {@code 409 Conflict} ({@code RULE_SET_FROZEN}).
 * Renaming a snapshot stays allowed.
 */
public class RuleSetEditBlockedException extends RuntimeException {

    public RuleSetEditBlockedException(String message) {
        super(message);
    }
}
