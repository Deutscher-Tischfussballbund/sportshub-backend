package de.dtfb.sportshub.backend.importer;

public record ImportCountDto(ImportRecordType recordType, ImportAction action, long count) {
}
