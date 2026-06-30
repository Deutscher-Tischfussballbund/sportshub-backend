package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class PoolNotFoundException extends NotFoundExceptionMarker {
    public PoolNotFoundException(String id) {
        super("pool", "POOL_NOT_FOUND", id);
    }
}
