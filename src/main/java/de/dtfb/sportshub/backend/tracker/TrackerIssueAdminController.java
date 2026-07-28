package de.dtfb.sportshub.backend.tracker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Consumed by the plain static tracker page ({@code /tracker/index.html}). Everything here is
 * public (see {@code TrackerSecurityConfig} — the whole surface is {@code permitAll}) except
 * convert/delete, which require a GitHub login in the required org via {@code isAuthenticated()}.
 */
@RestController
@RequestMapping("/v1/tracker/issues")
public class TrackerIssueAdminController {

    private final TrackerIssueService service;

    public TrackerIssueAdminController(TrackerIssueService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrackerIssueDto> listIssues(@RequestParam(required = false) String voterName) {
        return service.list(voterName);
    }

    @PostMapping
    public ResponseEntity<TrackerIssueDto> createIssue(@RequestBody TrackerIssueSubmission submission) {
        TrackerIssueDto dto = service.create(submission.getTitle(), submission.getDescription(),
            submission.getReportedBy());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/{id}/votes")
    public TrackerIssueDto voteForIssue(@PathVariable String id, @RequestBody TrackerIssueVoteRequest request) {
        return service.vote(id, request.getVoterName());
    }

    @DeleteMapping("/{id}/votes")
    public TrackerIssueDto unvoteIssue(@PathVariable String id, @RequestParam String voterName) {
        return service.unvote(id, voterName);
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("isAuthenticated()")
    public TrackerIssueDto convertIssue(@PathVariable String id, @RequestBody TrackerIssueConvertRequest request,
                                         @RegisteredOAuth2AuthorizedClient("github") OAuth2AuthorizedClient githubClient) {
        return service.convert(id, request.getGithubRepo(), githubClient.getAccessToken().getTokenValue());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public void deleteIssue(@PathVariable String id) {
        service.delete(id);
    }
}
