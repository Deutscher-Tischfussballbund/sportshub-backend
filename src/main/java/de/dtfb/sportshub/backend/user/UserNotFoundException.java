package de.dtfb.sportshub.backend.user;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class UserNotFoundException extends NotFoundExceptionMarker {
    public UserNotFoundException(String dtfbId) {
        super("User not found: " + dtfbId);
    }

    @Override
    public String getErrorCode() {
        return "USER_NOT_FOUND";
    }
}