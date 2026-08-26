package de.dtfb.sportshub.backend.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.List;

/**
 * Defining this bean makes Spring Boot's OAuth2-resource-server autoconfiguration back off, so we
 * reconstruct what it would have built (issuer/signature/expiry validation over the realm's JWKS)
 * and add one more check: the token's {@code azp} (authorized party -- which Keycloak client
 * requested it) must be in {@code sportshub.security.allowed-clients}. Without this, the resource
 * server accepts any valid dtfb-realm token regardless of client (docs/10 §5, now settled).
 *
 * <p>Always resolves JWKS via {@code withJwkSetUri} (never {@code withIssuerLocation}/OIDC
 * discovery), so key fetching stays lazy (first token, not at boot) in every profile -- prod
 * already relies on this to boot even if Keycloak isn't up yet (see application-prod.yaml). Dev
 * has no explicit {@code jwk-set-uri}, so it's derived from {@code issuer-uri} using Keycloak's
 * well-known JWKS path.
 */
@Configuration
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(
        @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri,
        @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:}") String jwkSetUri,
        @Value("${sportshub.security.allowed-clients}") List<String> allowedClients) {

        String effectiveJwkSetUri = jwkSetUri.isBlank()
            ? issuerUri + "/protocol/openid-connect/certs"
            : jwkSetUri;

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(effectiveJwkSetUri).build();

        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> audienceValidator =
            new JwtClaimValidator<String>("azp", allowedClients::contains);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));

        return decoder;
    }
}
