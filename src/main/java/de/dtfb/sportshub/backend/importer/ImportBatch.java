package de.dtfb.sportshub.backend.importer;

import java.time.Instant;
import java.util.List;

/**
 * What an {@link ImportSource} makes of a file: a header and neutral records keyed by the source's
 * own ids. Nothing in here refers to Sports Hub ids -- matching is the planner's job.
 */
public record ImportBatch(Header header,
                          List<ImportedFederation> federations,
                          List<ImportedClub> clubs,
                          List<ImportedPlayer> players,
                          List<ImportedMembership> memberships,
                          List<ImportedSeason> seasons,
                          List<ImportedLeague> leagues,
                          List<ImportedTeam> teams,
                          List<ImportedRosterEntry> rosterEntries,
                          List<ImportedFixture> fixtures) {

    public ImportBatch {
        federations = List.copyOf(federations);
        clubs = List.copyOf(clubs);
        players = List.copyOf(players);
        memberships = List.copyOf(memberships);
        seasons = List.copyOf(seasons);
        leagues = List.copyOf(leagues);
        teams = List.copyOf(teams);
        rosterEntries = List.copyOf(rosterEntries);
        fixtures = List.copyOf(fixtures);
    }

    /** Master data only (docs/28). */
    public ImportBatch(Header header, List<ImportedFederation> federations, List<ImportedClub> clubs,
                       List<ImportedPlayer> players, List<ImportedMembership> memberships) {
        this(header, federations, clubs, players, memberships, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    /**
     * @param instance   the installation the data comes from (e.g. {@code tfvhh}) -- source ids are only
     *                   unique within one installation
     * @param anonymized pseudonymized for a test system (docs/28)
     */
    public record Header(String source, String instance, Instant exportedAt, int formatVersion, boolean anonymized) {
    }
}
