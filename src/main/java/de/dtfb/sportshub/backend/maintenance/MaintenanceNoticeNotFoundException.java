package de.dtfb.sportshub.backend.maintenance;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class MaintenanceNoticeNotFoundException extends NotFoundExceptionMarker {
    public MaintenanceNoticeNotFoundException(String id) {
        super("maintenance notice", "MAINTENANCE_NOTICE_NOT_FOUND", id);
    }
}
