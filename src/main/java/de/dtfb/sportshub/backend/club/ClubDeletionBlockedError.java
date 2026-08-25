package de.dtfb.sportshub.backend.club;

/** 409 response body when a club delete is blocked by teams or active members still attached. */
public record ClubDeletionBlockedError(String code, String message) {
}
