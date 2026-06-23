package de.dtfb.sportshub.backend.access.role;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * The role catalog: a static, self-describing list of every {@link Role} the system knows, with the
 * {@link ScopeType} a grant of it implies and a deprecation flag. Derived from the enum so it cannot
 * drift — the frontend consumes it (e.g. to populate role pickers) instead of hardcoding the set.
 *
 * <p>Distinct from {@code /v1/admin/auth/grantable-scopes} (what the <em>current user</em> may grant)
 * and {@code /v1/auth/me/roles} (what they hold). This is the catalog itself, readable by any
 * authenticated user.
 */
@RestController
@RequestMapping("/v1/auth/roles")
public class RoleCatalogController {

    @GetMapping
    public List<RoleCatalogEntryDto> roles() {
        return Arrays.stream(Role.values())
            .map(role -> new RoleCatalogEntryDto(role, role.scopeType(), isDeprecated(role)))
            .toList();
    }

    private static boolean isDeprecated(Role role) {
        try {
            return Role.class.getField(role.name()).isAnnotationPresent(Deprecated.class);
        } catch (NoSuchFieldException e) {
            // Every enum constant has a matching field; this is unreachable.
            throw new IllegalStateException("Missing field for role " + role, e);
        }
    }
}
