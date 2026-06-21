package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class PlayerNotFoundException extends NotFoundExceptionMarker {
    public PlayerNotFoundException(String id) {
        super("player", "PLAYER_NOT_FOUND", id);
    }
}
