package de.dtfb.sportshub.backend.tracker;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A tester-reported item on the test-system feedback tracker. Starts {@link TrackerIssueStatus#OPEN};
 * {@link #convert(String, String)} moves it to {@link TrackerIssueStatus#APPROVED} once a real
 * GitHub issue has been filed for it.
 */
@Entity
@Getter
@Setter
public class TrackerIssue extends BaseEntity {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TrackerIssueStatus status = TrackerIssueStatus.OPEN;

    private String reportedBy;

    private String githubRepo;

    private String githubIssueUrl;

    @Column(nullable = false)
    private Instant createdAt;

    void convert(String githubRepo, String githubIssueUrl) {
        this.githubRepo = githubRepo;
        this.githubIssueUrl = githubIssueUrl;
        this.status = TrackerIssueStatus.APPROVED;
    }
}
