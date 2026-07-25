package de.dtfb.sportshub.backend.league;

/** 409 response body when a league delete is blocked by an existing tier or participation. */
public record LeagueDeletionBlockedError(String code, String message) {
}
