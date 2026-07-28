package de.dtfb.sportshub.backend.tracker;

import org.jspecify.annotations.NonNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class TrackerIssueService {

    private final TrackerIssueRepository issueRepository;
    private final TrackerIssueVoteRepository voteRepository;
    private final GitHubIssueClient gitHubIssueClient;

    public TrackerIssueService(TrackerIssueRepository issueRepository,
                                TrackerIssueVoteRepository voteRepository,
                                GitHubIssueClient gitHubIssueClient) {
        this.issueRepository = issueRepository;
        this.voteRepository = voteRepository;
        this.gitHubIssueClient = gitHubIssueClient;
    }

    @Transactional
    public TrackerIssueDto create(String title, String description, String reportedBy) {
        TrackerIssue issue = new TrackerIssue();
        issue.setTitle(title);
        issue.setDescription(description);
        issue.setReportedBy(reportedBy);
        issue.setCreatedAt(Instant.now());

        return toDto(issueRepository.save(issue), 0, false);
    }

    @Transactional(readOnly = true)
    public List<TrackerIssueDto> list(String voterName) {
        List<TrackerIssue> issues = issueRepository.findAll();
        Map<String, Long> voteCounts = issues.stream()
            .collect(java.util.stream.Collectors.toMap(TrackerIssue::getId,
                issue -> voteRepository.countByIssueId(issue.getId())));

        return issues.stream()
            .sorted(Comparator
                .comparing((TrackerIssue issue) -> voteCounts.get(issue.getId())).reversed()
                .thenComparing(TrackerIssue::getCreatedAt))
            .map(issue -> toDto(issue, voteCounts.get(issue.getId()),
                voterName != null && voteRepository.findByIssueIdAndVoterName(issue.getId(), voterName).isPresent()))
            .toList();
    }

    @Transactional
    public TrackerIssueDto vote(String issueId, String voterName) {
        TrackerIssue issue = getIssue(issueId);

        if (voteRepository.findByIssueIdAndVoterName(issueId, voterName).isEmpty()) {
            TrackerIssueVote vote = new TrackerIssueVote();
            vote.setIssue(issue);
            vote.setVoterName(voterName);
            try {
                voteRepository.save(vote);
            } catch (DataIntegrityViolationException ignored) {
                // Lost a race against a duplicate vote from the same name — already recorded.
            }
        }

        return toDto(issue, voteRepository.countByIssueId(issueId), true);
    }

    @Transactional
    public TrackerIssueDto unvote(String issueId, String voterName) {
        TrackerIssue issue = getIssue(issueId);
        voteRepository.deleteByIssueIdAndVoterName(issueId, voterName);

        return toDto(issue, voteRepository.countByIssueId(issueId), false);
    }

    @Transactional
    public TrackerIssueDto convert(String issueId, String githubRepo, String accessToken) {
        TrackerIssue issue = getIssue(issueId);

        String body = (issue.getDescription() == null ? "" : issue.getDescription())
            + "\n\n---\nReported by " + issue.getReportedBy() + " via the test-system tracker.";
        String url = gitHubIssueClient.createIssue(githubRepo, issue.getTitle(), body, accessToken);

        issue.convert(githubRepo, url);
        issueRepository.save(issue);

        return toDto(issue, voteRepository.countByIssueId(issueId), false);
    }

    @Transactional
    public void delete(String issueId) {
        TrackerIssue issue = getIssue(issueId);
        voteRepository.deleteAll(voteRepository.findByIssueId(issueId));
        issueRepository.delete(issue);
    }

    private @NonNull TrackerIssue getIssue(String id) {
        return issueRepository.findById(id).orElseThrow(() -> new TrackerIssueNotFoundException(id));
    }

    private TrackerIssueDto toDto(TrackerIssue issue, long voteCount, boolean votedByMe) {
        TrackerIssueDto dto = new TrackerIssueDto();
        dto.setId(issue.getId());
        dto.setTitle(issue.getTitle());
        dto.setDescription(issue.getDescription());
        dto.setStatus(issue.getStatus());
        dto.setReportedBy(issue.getReportedBy());
        dto.setGithubRepo(issue.getGithubRepo());
        dto.setGithubIssueUrl(issue.getGithubIssueUrl());
        dto.setCreatedAt(issue.getCreatedAt());
        dto.setVoteCount(voteCount);
        dto.setVotedByMe(votedByMe);
        return dto;
    }
}
