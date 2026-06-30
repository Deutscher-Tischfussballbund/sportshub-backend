package de.dtfb.sportshub.backend.season;

/** 409 response body when a season delete is blocked by existing results. */
public record SeasonDeletionBlockedError(String code, String message, SeasonContents attached) {
}
