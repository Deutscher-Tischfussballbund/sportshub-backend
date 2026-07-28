package de.dtfb.sportshub.backend.tracker;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class TrackerIssueDto {
    private String id;
    private String title;
    private String description;
    private TrackerIssueStatus status;
    private String reportedBy;
    private String githubRepo;
    private String githubIssueUrl;
    private Instant createdAt;
    private long voteCount;
    private boolean votedByMe;
}
