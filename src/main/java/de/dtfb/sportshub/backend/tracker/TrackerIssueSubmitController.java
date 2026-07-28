package de.dtfb.sportshub.backend.tracker;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reached from the admin app's feedback bubble (test-system builds only) — any authenticated
 * user, gated by the app's normal Keycloak JWT (main {@code SecurityConfig} chain), not the
 * Basic Auth used by the plain tracker page/{@link TrackerIssueAdminController}.
 */
@RestController
@RequestMapping("/v1/tracker")
public class TrackerIssueSubmitController {

    private final TrackerIssueService service;

    public TrackerIssueSubmitController(TrackerIssueService service) {
        this.service = service;
    }

    @PostMapping("/submit")
    public ResponseEntity<TrackerIssueDto> submitIssue(@RequestBody TrackerIssueSubmission submission,
                                                        @AuthenticationPrincipal Jwt jwt) {
        TrackerIssueDto dto = service.create(submission.getTitle(), submission.getDescription(), reportedByFrom(jwt));
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    private String reportedByFrom(Jwt jwt) {
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");
        if (givenName != null || familyName != null) {
            return ((givenName == null ? "" : givenName) + " " + (familyName == null ? "" : familyName)).trim();
        }
        return jwt.getClaimAsString("email");
    }
}
