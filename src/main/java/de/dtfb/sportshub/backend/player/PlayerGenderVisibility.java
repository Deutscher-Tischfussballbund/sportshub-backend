package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.access.auth.AuthorizationService;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strips the admin-only {@link PlayerDto#getGenderDetail()} (the divers league side) from player
 * DTOs unless the caller holds an admin role. Applied at the two post-mapping hooks every player
 * read passes through ({@link PlayerService}, {@link PlayerDirectoryService}), so the rule lives in
 * one place rather than per endpoint.
 */
@Component
public class PlayerGenderVisibility {

    private final AuthorizationService authz;

    public PlayerGenderVisibility(AuthorizationService authz) {
        this.authz = authz;
    }

    public void apply(List<PlayerDto> dtos) {
        if (dtos.isEmpty() || authz.hasAnyAdminRole()) {
            return;
        }
        dtos.forEach(dto -> dto.setGenderDetail(null));
    }
}
