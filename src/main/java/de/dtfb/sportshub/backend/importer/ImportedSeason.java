package de.dtfb.sportshub.backend.importer;

import java.time.LocalDate;

/** A past season (docs/29). The SM keeps no dates on a season: the adapter takes them from its leagues. */
public record ImportedSeason(String externalId, String name, LocalDate startDate, LocalDate endDate) {
}
