package de.dtfb.sportshub.backend.maintenance;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * While a maintenance window marked read-only runs (SPO-119), every change is refused with
 * {@code 423 MAINTENANCE_READ_ONLY} -- centrally, so no write endpoint can slip through. Reads stay open.
 * Global admins are exempt: they do the maintenance (imports, fixes) and may end the window early, so
 * the maintenance-notice endpoints stay open too. 423 rather than 503 on purpose: the frontend reads 503
 * as "backend unreachable" and leaves for its maintenance page, while reading still works here.
 * Not a Spring bean on purpose (it would run outside the security chain); SecurityConfig adds it after
 * authentication, so the caller's roles are known.
 */
public class MaintenanceReadOnlyFilter extends OncePerRequestFilter {

    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private static final String NOTICE_PATH = "/v1/admin/maintenance-notices";

    private final MaintenanceNoticeService notices;
    private final AuthorizationService authz;

    public MaintenanceReadOnlyFilter(MaintenanceNoticeService notices, AuthorizationService authz) {
        this.notices = notices;
        this.authz = authz;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        if (READ_METHODS.contains(request.getMethod())
            || request.getRequestURI().startsWith(NOTICE_PATH)
            || !notices.isReadOnlyNow()
            || authz.isAdmin()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(423);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
            {"code":"MAINTENANCE_READ_ONLY","message":"Maintenance in progress -- changes are not possible right now"}""");
    }
}
