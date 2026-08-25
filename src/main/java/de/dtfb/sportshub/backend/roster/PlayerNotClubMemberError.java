package de.dtfb.sportshub.backend.roster;

/** 409 response body when adding a player to a roster is blocked by missing club membership. */
public record PlayerNotClubMemberError(String code, String message) {
}
