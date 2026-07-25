package de.dtfb.sportshub.backend.federation;

/** 409 response body when a federation default rule-set change is blocked by a running tier. */
public record FederationDefaultRuleSetChangeBlockedError(String code, String message) {
}
