package de.dtfb.sportshub.backend.tracker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

/**
 * Gates the tracker's GitHub login to members of one org: after the normal GitHub OAuth2 profile
 * fetch, calls {@code GET /user/orgs} with the user's own access token (needs the {@code read:org}
 * scope, requested in the client registration) and rejects the login outright if the required org
 * isn't in the list. This runs at login time, not per-request — a rejected login never gets a
 * session, so {@code @PreAuthorize("isAuthenticated()")} on convert/delete is sufficient downstream.
 */
@Service
public class GitHubOrgOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(GitHubOrgOAuth2UserService.class);

    private final String requiredOrg;
    private final RestClient restClient;

    public GitHubOrgOAuth2UserService(@Value("${tracker.github-org}") String requiredOrg) {
        this.requiredOrg = requiredOrg;
        this.restClient = RestClient.builder().baseUrl("https://api.github.com").build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User user = super.loadUser(userRequest);

        if (!isMemberOfRequiredOrg(userRequest.getAccessToken().getTokenValue())) {
            throw new OAuth2AuthenticationException(
                new OAuth2Error("not_org_member"),
                "Not a member of the " + requiredOrg + " GitHub organization");
        }

        return user;
    }

    private boolean isMemberOfRequiredOrg(String accessToken) {
        List<Map<String, Object>> orgs;
        try {
            orgs = restClient.get()
                .uri("/user/orgs?per_page=100")
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientResponseException ex) {
            // Surfaces as a clean login failure (routed to the configured failureUrl) instead of an
            // unhandled exception — most likely causes: the "read:org" scope wasn't granted, or the
            // org's OAuth App access policy is restricting third-party apps (GitHub org settings →
            // Third-party access).
            log.warn("GitHub org-membership check failed ({}): {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OAuth2AuthenticationException(
                new OAuth2Error("org_membership_check_failed"),
                "Could not verify GitHub org membership: " + ex.getStatusCode());
        }

        return orgs != null && orgs.stream().anyMatch(org -> requiredOrg.equalsIgnoreCase((String) org.get("login")));
    }
}
