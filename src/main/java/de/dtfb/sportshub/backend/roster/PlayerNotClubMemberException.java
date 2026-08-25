package de.dtfb.sportshub.backend.roster;

/**
 * Thrown when {@code RosterService.addPlayer} is refused because the player isn't an active member
 * of the team's club yet -- club membership is a precondition for being rostered onto one of that
 * club's teams (see {@code ClubMembershipService}). Mapped to {@code 409 Conflict}.
 */
public class PlayerNotClubMemberException extends RuntimeException {

    public PlayerNotClubMemberException(String message) {
        super(message);
    }
}
