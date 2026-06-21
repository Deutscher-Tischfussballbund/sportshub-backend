package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class DisciplineNotFoundException extends NotFoundExceptionMarker {
    public DisciplineNotFoundException(String id) {
        super("discipline", "DISCIPLINE_NOT_FOUND", id);
    }
}
