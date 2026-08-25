package de.dtfb.sportshub.backend.user;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class UserNotFoundException extends NotFoundExceptionMarker {
    public UserNotFoundException(String id) {
        super("user", "USER_NOT_FOUND", id);
    }
}
