package de.dtfb.sportshub.backend.group;

/** 409 response body when a group delete is blocked by a team still placed in it. */
public record GroupDeletionBlockedError(String code, String message) {
}
