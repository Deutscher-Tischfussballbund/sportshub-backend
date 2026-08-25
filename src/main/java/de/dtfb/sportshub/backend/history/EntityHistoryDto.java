package de.dtfb.sportshub.backend.history;

import java.time.Instant;

public record EntityHistoryDto(
    String fieldName,
    String oldValue,
    String newValue,
    Instant changedAt,
    String changedByDtfbId
) {
}
