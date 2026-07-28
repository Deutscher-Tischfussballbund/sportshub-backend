package de.dtfb.sportshub.backend.tracker;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TrackerIssueVoteRepository extends JpaRepository<TrackerIssueVote, String> {

    List<TrackerIssueVote> findByIssueId(String issueId);

    Optional<TrackerIssueVote> findByIssueIdAndVoterName(String issueId, String voterName);

    long countByIssueId(String issueId);

    void deleteByIssueIdAndVoterName(String issueId, String voterName);
}
