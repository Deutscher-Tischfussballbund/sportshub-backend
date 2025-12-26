package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class EventNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "EVENT_NOT_FOUND";

    public EventNotFoundException(String uuid) {
        super("Could not find event with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
