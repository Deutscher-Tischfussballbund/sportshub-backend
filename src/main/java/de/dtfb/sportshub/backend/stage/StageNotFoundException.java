package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class StageNotFoundException extends NotFoundExceptionMarker {
    public StageNotFoundException(String id) {
        super("stage", "STAGE_NOT_FOUND", id);
    }
}
