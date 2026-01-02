package de.dtfb.sportshub.backend.phase;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class PhaseNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "PHASE_NOT_FOUND";

    public PhaseNotFoundException(String uuid) {
        super("Could not find phase with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
