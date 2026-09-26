package de.dtfb.sportshub.backend.leaguerules;

/** 409 response body when a rule-set template name is already taken; {@code existingId} names that template. */
public record RuleSetNameTakenError(String code, String message, String existingId) {
}
