package de.dtfb.sportshub.backend.access.apiclient;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class ApiClientGrantNotFoundException extends NotFoundExceptionMarker {
    public ApiClientGrantNotFoundException(String id) {
        super("apiClientGrant", "API_CLIENT_GRANT_NOT_FOUND", id);
    }
}
