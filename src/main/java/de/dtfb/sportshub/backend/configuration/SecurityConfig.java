package de.dtfb.sportshub.backend.configuration;

import de.dtfb.sportshub.backend.access.apikey.ApiKeyAuthenticationFilter;
import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import de.dtfb.sportshub.backend.maintenance.MaintenanceNoticeService;
import de.dtfb.sportshub.backend.maintenance.MaintenanceReadOnlyFilter;
import de.dtfb.sportshub.backend.access.apikey.ApiKeyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Allowed CORS origins for the API. Comma-separated; set per profile (see application-*.yaml). */
    private final List<String> allowedOrigins;
    private final ApiKeyService apiKeyService;
    private final MaintenanceNoticeService maintenanceNotices;
    private final AuthorizationService authz;

    public SecurityConfig(
        @Value("${sportshub.cors.allowed-origins}") List<String> allowedOrigins,
        ApiKeyService apiKeyService, MaintenanceNoticeService maintenanceNotices, AuthorizationService authz) {
        this.allowedOrigins = allowedOrigins;
        this.apiKeyService = apiKeyService;
        this.maintenanceNotices = maintenanceNotices;
        this.authz = authz;
    }

    // Falls through from TrackerSecurityConfig's chain (@Order(1), matches only /tracker/** and
    // /v1/tracker/issues/**) — this one must stay last since it matches every other request.
    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) {
        ApiErrorAuthenticationEntryPoint authenticationEntryPoint = new ApiErrorAuthenticationEntryPoint();
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            // H2 console renders in frames; allow same-origin framing so it isn't blocked
            .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // CORS preflight must never require a token
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // Swagger / OpenAPI - public
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // H2 console for dev
                .requestMatchers("/h2-console/**").permitAll()
                // Everything else requires authentication — reads and writes alike
                .anyRequest().authenticated()
            )
            // Read-only machine access via backend-issued API keys (X-API-Key), checked before the
            // JWT filter; requests without the header fall through to JWT auth unchanged.
            .addFilterBefore(new ApiKeyAuthenticationFilter(apiKeyService), BearerTokenAuthenticationFilter.class)
            // A running read-only maintenance window refuses changes (SPO-119) -- after authentication,
            // so global admins (who do the maintenance) can be let through.
            .addFilterAfter(new MaintenanceReadOnlyFilter(maintenanceNotices, authz), BearerTokenAuthenticationFilter.class)
            // 401s (missing or invalid credentials) carry an ApiError body like every other error.
            .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(_ -> {
                })
                .authenticationEntryPoint(authenticationEntryPoint)
            );

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
