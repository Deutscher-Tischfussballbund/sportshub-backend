package de.dtfb.sportshub.backend.location;

/** 409 body for {@link LocationDeletionBlockedException}; {@code fixtureCount} = fixtures at the venue. */
public record LocationDeletionBlockedError(String code, String message, long fixtureCount) {
}
