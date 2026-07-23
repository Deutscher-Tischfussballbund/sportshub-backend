package de.dtfb.sportshub.backend.teamparticipation;

/** 409 response body when a participation delete is blocked by recorded matches/standings. */
public record ParticipationDeletionBlockedError(String code, String message) {
}
