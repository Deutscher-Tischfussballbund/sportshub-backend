package de.dtfb.sportshub.backend.configuration;

import de.dtfb.sportshub.backend.tracker.GitHubOrgOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Standalone auth gate for the small self-hosted test-system tracker: the plain static page at
 * {@code /tracker/index.html} and its API ({@code /v1/tracker/issues/**}). Everything on this
 * surface is public (list/create/vote need no login at all — anyone can report or upvote); only
 * convert-to-GitHub and delete require a login, enforced by {@code @PreAuthorize("isAuthenticated()")}
 * on those two controller methods, not here. Login is GitHub OAuth2, gated to members of one org by
 * {@link GitHubOrgOAuth2UserService} — deliberately not Keycloak, since this is meant to map onto
 * real GitHub identities (who can actually action the resulting GitHub issue), not app users.
 *
 * <p>Ordered ahead of the main JWT chain (which has no securityMatcher, so it would otherwise
 * swallow these paths too). Session-based (not stateless) — OAuth2 login needs a session to carry
 * the authorization-request state and, afterwards, the logged-in principal.
 */
@Configuration
public class TrackerSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain trackerFilterChain(HttpSecurity http, GitHubOrgOAuth2UserService gitHubOrgOAuth2UserService)
        throws Exception {
        http
            .securityMatcher("/tracker/**", "/v1/tracker/issues/**", "/v1/tracker/me", "/oauth2/**", "/login/**", "/logout")
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(gitHubOrgOAuth2UserService))
                // No "saved request" exists for the login link itself (only for a challenged
                // request), so without this Spring's default post-login target is "/" — outside
                // this chain's securityMatcher, so it falls through to the JWT chain and 401s.
                .defaultSuccessUrl("/tracker/index.html", true)
                .failureUrl("/tracker/index.html?loginError"))
            .logout(logout -> logout.logoutSuccessUrl("/tracker/index.html"));

        return http.build();
    }
}
