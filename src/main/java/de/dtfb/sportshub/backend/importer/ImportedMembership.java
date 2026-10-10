package de.dtfb.sportshub.backend.importer;

import java.time.Instant;

/** A player's membership in a club; {@code left} = the source marks it as ended. Dates are optional. */
public record ImportedMembership(String playerExternalId, String clubExternalId, boolean left,
                                 Instant joinedAt, Instant leftAt) {

    /** Memberships have no id of their own in the source -- player and club identify them. */
    public String externalId() {
        return playerExternalId + ":" + clubExternalId;
    }
}
