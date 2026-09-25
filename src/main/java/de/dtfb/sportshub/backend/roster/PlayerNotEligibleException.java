package de.dtfb.sportshub.backend.roster;

import lombok.Getter;

import java.util.List;

/**
 * Thrown when {@code RosterService.addPlayer}/{@code submit} is refused because one or more players
 * fail the league category's eligibility profile ({@code CategoryEligibility}). Mapped to
 * {@code 409 Conflict} with code {@code PLAYER_NOT_ELIGIBLE}.
 */
@Getter
public class PlayerNotEligibleException extends RuntimeException {

    private final List<IneligiblePlayer> players;

    public PlayerNotEligibleException(String message, List<IneligiblePlayer> players) {
        super(message);
        this.players = List.copyOf(players);
    }
}
