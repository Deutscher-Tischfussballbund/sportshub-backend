package de.dtfb.sportshub.backend.maintenance;

import java.time.Instant;

/** {@code id} and {@code updatedAt} are read-only. */
public record MaintenanceNoticeDto(String id, String messageDe, String messageEn, Instant startsAt, Instant endsAt,
                                   Instant announceFrom, Boolean readOnly, Instant updatedAt) {

    static MaintenanceNoticeDto of(MaintenanceNotice notice) {
        return new MaintenanceNoticeDto(notice.getId(), notice.getMessageDe(), notice.getMessageEn(),
            notice.getStartsAt(), notice.getEndsAt(), notice.getAnnounceFrom(), notice.isReadOnly(),
            notice.getUpdatedAt());
    }
}
