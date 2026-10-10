package de.dtfb.sportshub.backend.importer;

/** One field an import would change, as display strings. */
public record FieldChange(String oldValue, String newValue) {
}
