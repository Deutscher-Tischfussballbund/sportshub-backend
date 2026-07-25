package de.dtfb.sportshub.backend.leaguerules;

/** 409 response body when a rule-set delete is blocked by a League/Tier/Federation reference. */
public record RuleSetDeletionBlockedError(String code, String message) {
}
