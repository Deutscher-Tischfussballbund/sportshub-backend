package de.dtfb.sportshub.backend.tier;

/** 409 response body when a tier delete is blocked by an existing group. */
public record TierDeletionBlockedError(String code, String message) {
}
