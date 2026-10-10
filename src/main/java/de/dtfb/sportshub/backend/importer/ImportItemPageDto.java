package de.dtfb.sportshub.backend.importer;

import java.util.List;

public record ImportItemPageDto(List<ImportItemDto> items, long total) {
}
