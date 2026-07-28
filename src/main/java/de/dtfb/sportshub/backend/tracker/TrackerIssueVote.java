package de.dtfb.sportshub.backend.tracker;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/** One tester's upvote on a {@link TrackerIssue}. One vote per (issue, voterName). */
@Entity
@Getter
@Setter
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"issue_id", "voter_name"}))
public class TrackerIssueVote extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "issue_id")
    private TrackerIssue issue;

    @Column(name = "voter_name", nullable = false)
    private String voterName;
}
