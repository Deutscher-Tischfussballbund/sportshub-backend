package de.dtfb.sportshub.backend.importer;

/** An organiser in the source (SM {@code veranstalter}). Federations are never created by an import, only mapped. */
public record ImportedFederation(String externalId, String name) {
}
