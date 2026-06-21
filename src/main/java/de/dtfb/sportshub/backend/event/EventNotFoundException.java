package de.dtfb.sportshub.backend.event;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class EventNotFoundException extends NotFoundExceptionMarker {
    public EventNotFoundException(String id) {
        super("event", "EVENT_NOT_FOUND", id);
    }
}
