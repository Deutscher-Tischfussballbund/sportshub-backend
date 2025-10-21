package de.dtfb.sportshub.backend.config.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

    private final Environment environment;

    private static final String[] OPENAPI_PATHS = {
        "/v3/api-docs/**",
        "/v3/api-docs.yaml",
        "/swagger-ui/**",
        "/swagger-ui.html"
    };

    public SecurityConfiguration(Environment environment) {
        this.environment = environment;
    }

    @Bean
    SecurityFilterChain resourceServerSecurityFilterChain(
        HttpSecurity http) throws Exception {
        http.oauth2ResourceServer(resourceServer ->
            resourceServer.jwt(jwtDecoder ->
                jwtDecoder.jwtAuthenticationConverter(jwtAuthenticationConverter())
            )
        );

        http.sessionManagement(sessions ->
            sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        ).csrf(AbstractHttpConfigurer::disable);

        http.authorizeHttpRequests(requests -> {
            if (environment.acceptsProfiles(Profiles.of("dev", "stage"))) {
                requests.requestMatchers(OPENAPI_PATHS).permitAll();
            }

            requests.anyRequest().authenticated(); // TODO: fine tuning here
        });

        http.csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(new KeyCloakRoleConverter());
        return converter;
    }
}
