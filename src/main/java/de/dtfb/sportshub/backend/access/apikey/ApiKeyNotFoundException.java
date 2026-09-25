package de.dtfb.sportshub.backend.access.apikey;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class ApiKeyNotFoundException extends NotFoundExceptionMarker {
    public ApiKeyNotFoundException(String id) {
        super("apiKey", "API_KEY_NOT_FOUND", id);
    }
}
