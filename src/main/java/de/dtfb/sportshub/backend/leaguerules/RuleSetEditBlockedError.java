package de.dtfb.sportshub.backend.leaguerules;

/** 409 response body when a rule-set edit is blocked by a closed-season reference. */
public record RuleSetEditBlockedError(String code, String message) {
}
