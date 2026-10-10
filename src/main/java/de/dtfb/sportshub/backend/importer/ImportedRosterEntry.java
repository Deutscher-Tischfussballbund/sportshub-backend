package de.dtfb.sportshub.backend.importer;

/** A player on a past team's roster (docs/29); {@code left} = removed during the season. */
public record ImportedRosterEntry(String playerExternalId, String teamExternalId, boolean left) {

    /** Roster entries have no id of their own in the source -- player and team identify them. */
    public String externalId() {
        return playerExternalId + ":" + teamExternalId;
    }
}
