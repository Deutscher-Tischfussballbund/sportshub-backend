package de.dtfb.sportshub.backend.access.apikey;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * The authentication of a request made with a valid API key. Deliberately not a {@code Jwt}: every
 * {@code @authz} check and user-scoped endpoint needs a human's JWT, so they refuse this caller.
 */
public class ApiKeyAuthentication extends AbstractAuthenticationToken {

    private final String apiKeyId;
    private final String apiKeyName;

    public ApiKeyAuthentication(ApiKey apiKey) {
        super(List.of(new SimpleGrantedAuthority("ROLE_API_KEY")));
        this.apiKeyId = apiKey.getId();
        this.apiKeyName = apiKey.getName();
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return apiKeyId;
    }

    @Override
    public String getName() {
        return apiKeyName;
    }
}
