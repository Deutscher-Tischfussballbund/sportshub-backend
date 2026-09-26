package de.dtfb.sportshub.backend.leaguerules;

import lombok.Getter;

/**
 * Thrown when a rule-set template (blueprint) would get a name another template of the same owner
 * (federation, or DTFB-wide) already has -- template names are unique per owner, case-insensitive
 * (docs/21). Mapped to {@code 409 RULE_SET_NAME_TAKEN}, carrying the existing template's id so a
 * client can offer to overwrite it instead. League/tier snapshots are exempt.
 */
@Getter
public class RuleSetNameTakenException extends RuntimeException {

    private final String existingId;

    public RuleSetNameTakenException(String name, String existingId) {
        super("A rule-set template named \"" + name + "\" already exists");
        this.existingId = existingId;
    }
}
