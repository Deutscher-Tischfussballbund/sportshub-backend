package de.dtfb.sportshub.backend.tracker;

public enum TrackerIssueStatus {
    OPEN,
    APPROVED,
    /** Resolved, whether or not it was ever converted to a real GitHub issue. */
    DONE
}
