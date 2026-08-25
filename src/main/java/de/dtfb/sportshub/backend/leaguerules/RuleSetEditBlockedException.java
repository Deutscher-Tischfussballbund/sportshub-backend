package de.dtfb.sportshub.backend.leaguerules;

/**
 * Thrown when a rule set's own rule-affecting fields are edited while it is already referenced by a
 * League/Tier of a closed (archived) season -- that would silently rewrite the effective rules
 * behind that season's finalized results. Mapped to {@code 409 Conflict} -- clone the rule set
 * instead (POST .../{id}/clone), or rename only.
 */
public class RuleSetEditBlockedException extends RuntimeException {

    public RuleSetEditBlockedException(String message) {
        super(message);
    }
}
