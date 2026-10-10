package de.dtfb.sportshub.backend.importer;

import java.time.LocalDate;

/**
 * One league (= one table) of a past season (docs/29). {@code tableRule} is the SM's
 * {@code tabellenwertung}: below zero (except -2, a manually placed table) a cup or knockout, which isn't
 * imported. Points for a win/draw follow from it.
 */
public record ImportedLeague(String externalId, String seasonExternalId, String federationExternalId, String name,
                             int tableRule, LocalDate firstDay, LocalDate lastDay, ImportedGameMode mode) {
}
