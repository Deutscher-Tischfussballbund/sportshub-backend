package de.dtfb.sportshub.backend.tracker;

import lombok.Getter;
import lombok.Setter;

/**
 * Body of the "add a tracker issue" request — used by both create endpoints. {@code reportedBy}
 * is only honoured by the Basic-Auth admin endpoint; the JWT-authenticated bubble endpoint
 * derives it from the token instead and ignores whatever the client sends.
 */
@Getter
@Setter
public class TrackerIssueSubmission {
    private String title;
    private String description;
    private String reportedBy;
}
