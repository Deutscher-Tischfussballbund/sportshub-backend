package de.dtfb.sportshub.backend.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Files a real GitHub issue for a converted {@link TrackerIssue}, using the converting user's own
 * GitHub OAuth2 access token (see {@code TrackerIssueAdminController#convertIssue}) — the issue is
 * created as them, not as a shared service-account identity. That token needs the {@code repo} scope
 * (not just {@code public_repo}) alongside the login scopes, since one conversion target
 * (dtfb-frontend-ng) is private.
 */
@Service
public class GitHubIssueClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubIssueClient.class);

    private final RestClient restClient;

    // apiBaseUrl is overridable (GitHubIssueClientTest points it at a local test server) so the
    // real request path/encoding can be verified without hitting the actual GitHub API.
    public GitHubIssueClient(@Value("${tracker.github.api-base-url:https://api.github.com}") String apiBaseUrl) {
        // Built directly (not via an injected RestClient.Builder) — Boot 4 doesn't always
        // autoconfigure that bean depending on which web starter modules are present, and this
        // client needs nothing from it (no message converters/interceptors to share).
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
    }

    /** Creates an issue in {@code owner/repo} and returns its html_url. */
    @SuppressWarnings("unchecked")
    public String createIssue(String repo, String title, String body, String accessToken) {
        String[] ownerAndName = repo == null ? new String[0] : repo.split("/", 2);
        if (ownerAndName.length != 2 || ownerAndName[0].isBlank() || ownerAndName[1].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Repository must be in \"owner/repo\" form, got: " + repo);
        }

        try {
            Map<String, Object> response = restClient.post()
                // Two path variables, not one "{repo}" containing a slash — a single variable's
                // value gets percent-encoded as one path segment (the "/" becomes %2F), which
                // doesn't match GitHub's route and 404s regardless of actual permissions.
                .uri("/repos/{owner}/{name}/issues", ownerAndName[0], ownerAndName[1])
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "sportshub-backend-tracker")
                .body(Map.of("title", title, "body", body == null ? "" : body))
                .retrieve()
                .body(Map.class);

            return response == null ? null : (String) response.get("html_url");
        } catch (RestClientResponseException ex) {
            // GitHub returns 404 (not 403) for a private repo the token can't reach, by design —
            // it never reveals whether the repo exists to a caller without access. Logged in full
            // here since the client only sees "Not Found", which alone doesn't say why.
            log.warn("GitHub rejected issue creation in {} ({}): {}", repo, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "GitHub rejected the issue: " + ex.getStatusText());
        }
    }
}
