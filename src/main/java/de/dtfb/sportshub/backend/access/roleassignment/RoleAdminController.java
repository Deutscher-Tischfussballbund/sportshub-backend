package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;

import de.dtfb.sportshub.backend.player.PlayerDto;
import de.dtfb.sportshub.backend.player.PlayerRegistryService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Role administration consumed by the frontend's RoleAdminService. Paths mirror
 * the generated OpenAPI client exactly (no {@code /api} prefix).
 */
@RestController
@RequestMapping("/v1/admin/auth")
public class RoleAdminController {

    private final PlayerRegistryService registry;
    private final RoleAdminService roleAdminService;

    public RoleAdminController(PlayerRegistryService registry, RoleAdminService roleAdminService) {
        this.registry = registry;
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/assignments")
    public List<RoleAssignmentViewDto> assignments(
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String playerId) {
        // Query-param enums arrive as wire values (e.g. "region_admin"); convert via Jackson semantics.
        Role parsedRole = role == null || role.isBlank() ? null : Role.fromValue(role);
        return roleAdminService.assignments(parsedRole, regionId, q, playerId);
    }

    @GetMapping("/grantable-scopes")
    public GrantableScopesDto grantableScopes(@AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.grantableScopes(registry.currentPlayer(jwt));
    }

    @GetMapping("/roles")
    public List<RoleAssignmentDto> listRoles(@RequestParam String playerId) {
        return roleAdminService.rolesForPlayer(playerId);
    }

    @PostMapping("/roles")
    @PreAuthorize("@authz.canGrant(#dto)")
    public RoleAssignmentDto grant(@RequestBody GrantRoleDto dto, @AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.grant(dto, jwt.getClaimAsString("dtfb_id"));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("@authz.canRevoke(#id)")
    public void revoke(@PathVariable String id) {
        roleAdminService.revoke(id);
    }

    @GetMapping("/player-search")
    public List<PlayerDto> playerSearch(@RequestParam(required = false) String q) {
        return registry.search(q);
    }
}
