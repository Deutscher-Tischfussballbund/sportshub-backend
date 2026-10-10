package de.dtfb.sportshub.backend.importer;

public record ImportedClub(String externalId, String name, String shortName, String city,
                           String federationExternalId, boolean active) {
}
