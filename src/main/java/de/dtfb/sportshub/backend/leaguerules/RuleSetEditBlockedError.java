package de.dtfb.sportshub.backend.leaguerules;

/** 409 response body when a rule change is refused because the owning season has ended (docs/21). */
public record RuleSetEditBlockedError(String code, String message) {
}
