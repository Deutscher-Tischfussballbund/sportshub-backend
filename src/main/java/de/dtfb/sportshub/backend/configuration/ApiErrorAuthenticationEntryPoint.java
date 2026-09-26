package de.dtfb.sportshub.backend.configuration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * 401 for a request without (valid) credentials, in the same {@code {code, message}} shape as every
 * other API error (SPO-66). Spring's default resource-server entry point only sets the status and a
 * {@code WWW-Authenticate} header with an empty body; that header behaviour is kept by delegating to
 * it, then the JSON body is added.
 */
public class ApiErrorAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        delegate.commence(request, response, authException);
        String message = request.getHeader(HttpHeaders.AUTHORIZATION) == null
            ? "Authentication required: send a bearer token (or, for reads, an X-API-Key)"
            : "The bearer token is invalid or expired";
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // Fixed texts only, so no JSON escaping is needed.
        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}");
    }
}
