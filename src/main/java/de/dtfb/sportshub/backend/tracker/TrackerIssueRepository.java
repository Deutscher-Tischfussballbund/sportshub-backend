package de.dtfb.sportshub.backend.tracker;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackerIssueRepository extends JpaRepository<TrackerIssue, String> {
}
