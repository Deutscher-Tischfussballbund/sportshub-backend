package de.dtfb.sportshub.backend.season;

import java.util.UUID;

public class SeasonNotFoundException extends RuntimeException {
    public SeasonNotFoundException(UUID uuid) {
        super("Could not find season with uuid " + uuid);
    }
}
