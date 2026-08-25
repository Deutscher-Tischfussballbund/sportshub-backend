package de.dtfb.sportshub.backend.access.bootstrap;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.roleassignment.GrantRoleDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAdminService;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solves the bootstrap chicken-and-egg: granting a role requires being an admin, so a fresh
 * database (where {@code access-seed.sql} is dev-only) would have no admin and nobody able to
 * create one. On startup, if {@code sportshub.bootstrap.admin-dtfb-id} is set, this ensures that
 * user exists and holds a GLOBAL {@code ADMIN} grant — mirroring Keycloak's own
 * {@code KC_BOOTSTRAP_ADMIN}. Idempotent: a no-op once the grant is in place, so it is safe to
 * leave configured across restarts. Unset (the dev default) → does nothing.
 */
@Component
public class BootstrapAdminInitializer {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final UserRepository userRepository;
    private final RoleAdminService roleAdminService;
    private final String adminDtfbId;

    public BootstrapAdminInitializer(UserRepository userRepository,
                                     RoleAdminService roleAdminService,
                                     @Value("${sportshub.bootstrap.admin-dtfb-id:}") String adminDtfbId) {
        this.userRepository = userRepository;
        this.roleAdminService = roleAdminService;
        this.adminDtfbId = adminDtfbId;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void ensureBootstrapAdmin() {
        if (adminDtfbId == null || adminDtfbId.isBlank()) {
            log.debug("No sportshub.bootstrap.admin-dtfb-id configured; skipping bootstrap admin.");
            return;
        }

        // Find or create the user. Mirrors UserRegistryService#currentUser's minimal creation —
        // profile fields stay null until this person actually logs in.
        User user = userRepository.findByDtfbId(adminDtfbId).orElseGet(() -> {
            User created = new User();
            created.setDtfbId(adminDtfbId);
            return userRepository.save(created);
        });

        // grant() is idempotent (returns the existing assignment if present), so re-runs no-op.
        // ADMIN is always GLOBAL-scoped, hence a null scopeId.
        roleAdminService.grant(new GrantRoleDto(user.getId(), Role.ADMIN, null), "system-bootstrap");
        log.warn("Bootstrap admin ensured: GLOBAL ADMIN granted to dtfb_id '{}'.", adminDtfbId);
    }
}
