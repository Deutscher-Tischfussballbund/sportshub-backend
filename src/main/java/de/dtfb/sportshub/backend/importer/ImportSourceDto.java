package de.dtfb.sportshub.backend.importer;

import java.util.Set;

public record ImportSourceDto(String key, Set<ImportRecordType> recordTypes) {
}
