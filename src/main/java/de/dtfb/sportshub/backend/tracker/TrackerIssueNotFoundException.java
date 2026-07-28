package de.dtfb.sportshub.backend.tracker;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class TrackerIssueNotFoundException extends NotFoundExceptionMarker {
    public TrackerIssueNotFoundException(String id) {
        super("tracker issue", "TRACKER_ISSUE_NOT_FOUND", id);
    }
}
