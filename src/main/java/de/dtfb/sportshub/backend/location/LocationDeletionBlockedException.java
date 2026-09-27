package de.dtfb.sportshub.backend.location;

/** A venue can't be deleted while fixtures are scheduled at it -- reassign or clear them first. */
public class LocationDeletionBlockedException extends RuntimeException {

    private final long fixtureCount;

    public LocationDeletionBlockedException(long fixtureCount) {
        super("Venue is used by " + fixtureCount + " fixture(s)");
        this.fixtureCount = fixtureCount;
    }

    public long getFixtureCount() {
        return fixtureCount;
    }
}
