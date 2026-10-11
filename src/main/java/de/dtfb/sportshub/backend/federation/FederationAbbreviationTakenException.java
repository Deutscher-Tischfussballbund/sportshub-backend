package de.dtfb.sportshub.backend.federation;

/**
 * Thrown when creating/updating a federation would reuse another federation's abbreviation (compared
 * case-insensitively). Mapped to {@code 409 Conflict} with code {@code FEDERATION_ABBREVIATION_TAKEN}.
 */
public class FederationAbbreviationTakenException extends RuntimeException {

    public FederationAbbreviationTakenException(String abbreviation) {
        super("Another federation already uses the abbreviation '" + abbreviation + "'");
    }
}
