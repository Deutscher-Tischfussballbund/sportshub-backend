package de.dtfb.sportshub.backend.teamparticipation;

/** 409 response body when registering for a league is refused because its season has ended. */
public record SeasonEndedError(String code, String message, String endDate) {
}
