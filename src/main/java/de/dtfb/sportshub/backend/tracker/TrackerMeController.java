package de.dtfb.sportshub.backend.tracker;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lets the plain tracker page ask "am I logged in" without hitting a 401 first. Public itself. */
@RestController
public class TrackerMeController {

    @GetMapping("/v1/tracker/me")
    public TrackerMeDto trackerMe(@AuthenticationPrincipal OAuth2User user) {
        if (user == null) {
            return new TrackerMeDto(false, null);
        }
        Object login = user.getAttributes().get("login");
        return new TrackerMeDto(true, login == null ? null : login.toString());
    }
}
