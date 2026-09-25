package de.dtfb.sportshub.backend.access.apikey;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * Authenticates a request carrying an {@value #HEADER} header, before the JWT filter runs.
 * API keys are read-only by construction -- enforced here, centrally, not per endpoint -- so no
 * write endpoint can ever accept one, however it is gated:
 * <ul>
 *   <li>unknown, inactive or expired key → 401 {@code INVALID_API_KEY}</li>
 *   <li>any method other than GET/HEAD → 403 {@code API_KEY_READ_ONLY}</li>
 *   <li>{@code /v1/admin/**} or {@code /v1/auth/**} → 403 {@code API_KEY_PATH_NOT_ALLOWED}: user- and
 *       admin-scoped reads (identities, role assignments, ...) are for logged-in humans only</li>
 *   <li>key plus an {@code Authorization} header → 400: a request is either a user or a machine</li>
 * </ul>
 * Not a Spring bean on purpose -- Boot would register a bean {@code Filter} for every request,
 * outside the security chain; SecurityConfig adds it to its chain instead.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-API-Key";
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD");

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthenticationFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER);
        if (rawKey == null) {
            chain.doFilter(request, response);
            return;
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            reject(response, HttpServletResponse.SC_BAD_REQUEST, "BAD_REQUEST",
                "Send either an API key or an Authorization header, not both");
            return;
        }
        Optional<ApiKey> apiKey = apiKeyService.authenticate(rawKey.trim());
        if (apiKey.isEmpty()) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "INVALID_API_KEY",
                "The API key is unknown, deactivated or expired");
            return;
        }
        if (!READ_METHODS.contains(request.getMethod())) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "API_KEY_READ_ONLY",
                "API keys are read-only; writes require a user login");
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/v1/admin/") || path.startsWith("/v1/auth/")) {
            reject(response, HttpServletResponse.SC_FORBIDDEN, "API_KEY_PATH_NOT_ALLOWED",
                "API keys can't access admin or user endpoints");
            return;
        }
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new ApiKeyAuthentication(apiKey.get()));
        SecurityContextHolder.setContext(context);
        chain.doFilter(request, response);
    }

    /** Same {code, message} shape as GlobalExceptionHandler's ApiError; fixed texts, so no escaping needed. */
    private static void reject(HttpServletResponse response, int status, String code, String message)
        throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
