package de.dtfb.sportshub.backend.discipline;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class DisciplineNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "DISCIPLINE_NOT_FOUND";

    public DisciplineNotFoundException(String uuid) {
        super("Could not find discipline with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
