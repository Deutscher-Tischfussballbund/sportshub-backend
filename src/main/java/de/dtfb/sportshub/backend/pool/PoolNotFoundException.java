package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class PoolNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "POOL_NOT_FOUND";

    public PoolNotFoundException(String uuid) {
        super("Could not find pool with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
