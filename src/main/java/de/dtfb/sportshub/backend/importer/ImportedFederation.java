package de.dtfb.sportshub.backend.importer;

/**
 * An organiser in the source (SM {@code veranstalter}). Federations are never created by an import, only
 * mapped: by an earlier link, then by {@code abbreviation}, then by name (docs/28).
 */
public record ImportedFederation(String externalId, String name, String abbreviation) {
}
