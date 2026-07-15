package de.dtfb.sportshub.backend.tier;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class TierNotFoundException extends NotFoundExceptionMarker {
    public TierNotFoundException(String id) {
        super("tier", "TIER_NOT_FOUND", id);
    }
}
