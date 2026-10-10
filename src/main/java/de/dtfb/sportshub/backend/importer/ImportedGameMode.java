package de.dtfb.sportshub.backend.importer;

import de.dtfb.sportshub.backend.match.MatchType;

import java.util.List;

/**
 * How a league's fixtures were played (docs/29): the game plan in order ({@code null} = a game with a
 * result only, no players), and for a race the target score of the whole fixture (e.g. 42).
 */
public record ImportedGameMode(String externalId, String name, List<MatchType> gamePlan, Integer raceTarget) {

    public ImportedGameMode {
        gamePlan = gamePlan == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(gamePlan));
    }
}
