package de.dtfb.sportshub.backend.importer;

/**
 * Whether this instance takes pseudonymized exports (docs/28), {@code sportshub.importer.anonymized}.
 * The test system requires them so real data never lands there; production forbids them.
 */
public enum AnonymizationPolicy {
    ALLOWED,
    REQUIRED,
    FORBIDDEN
}
