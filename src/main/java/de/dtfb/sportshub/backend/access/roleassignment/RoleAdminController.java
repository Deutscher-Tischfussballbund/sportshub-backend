package de.dtfb.sportshub.backend.access.roleassignment;
import de.dtfb.sportshub.backend.access.role.Role;

import de.dtfb.sportshub.backend.user.UserDto;
import de.dtfb.sportshub.backend.user.UserRegistryService;
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

    private final UserRegistryService registry;
    private final RoleAdminService roleAdminService;

    public RoleAdminController(UserRegistryService registry, RoleAdminService roleAdminService) {
        this.registry = registry;
        this.roleAdminService = roleAdminService;
    }

    @GetMapping("/assignments")
    public List<RoleAssignmentViewDto> assignments(
        @RequestParam(required = false) String role,
        @RequestParam(required = false) String regionId,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) String userId) {
        // Query-param enums arrive as wire values (e.g. "region_admin"); convert via Jackson semantics.
        Role parsedRole = role == null || role.isBlank() ? null : Role.fromValue(role);
        return roleAdminService.assignments(parsedRole, regionId, q, userId);
    }

    @GetMapping("/grantable-scopes")
    public GrantableScopesDto grantableScopes(@AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.grantableScopes(registry.currentUser(jwt));
    }

    @GetMapping("/roles")
    public List<RoleAssignmentDto> listRoles(@RequestParam String userId) {
        return roleAdminService.rolesForUser(userId);
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

    /** Identity search for granting roles -- searches {@link de.dtfb.sportshub.backend.user.User}s, not
     *  the {@code Player} directory. The roster "add player" flow uses a separate search. */
    @GetMapping("/user-search")
    public List<UserDto> userSearch(@RequestParam(required = false) String q) {
        return registry.search(q);
    }
}
