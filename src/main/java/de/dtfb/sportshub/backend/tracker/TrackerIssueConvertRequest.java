package de.dtfb.sportshub.backend.tracker;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TrackerIssueConvertRequest {
    /** "owner/repo" — the target GitHub repository to file the issue in. */
    private String githubRepo;
}
