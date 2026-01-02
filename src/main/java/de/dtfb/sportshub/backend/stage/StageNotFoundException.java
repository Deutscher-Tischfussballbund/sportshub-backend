package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class StageNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "STAGE_NOT_FOUND";

    public StageNotFoundException(String uuid) {
        super("Could not find stage with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
