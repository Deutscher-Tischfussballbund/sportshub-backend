package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.area.AreaDto;
import de.dtfb.sportshub.backend.access.area.AreaService;
import de.dtfb.sportshub.backend.access.area.MeAreasResponseDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAdminService;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentDto;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserMapper;
import de.dtfb.sportshub.backend.user.UserRegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Identity surface consumed by the admin frontend's AuthService/AreaService.
 * Every call resolves (and lazily persists) the login identity behind the bearer token.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthMeController {

    private final UserRegistryService registry;
    private final RoleAdminService roleAdminService;
    private final AreaService areaService;
    private final UserMapper userMapper;

    public AuthMeController(UserRegistryService registry,
                           RoleAdminService roleAdminService,
                           AreaService areaService,
                           UserMapper userMapper) {
        this.registry = registry;
        this.roleAdminService = roleAdminService;
        this.areaService = areaService;
        this.userMapper = userMapper;
    }

    @GetMapping("/me")
    public MeResponseDto me(@AuthenticationPrincipal Jwt jwt) {
        User user = registry.currentUser(jwt);
        return new MeResponseDto(userMapper.toDto(user), roleAdminService.myRoleDtos(user));
    }

    @GetMapping("/me/areas")
    public MeAreasResponseDto myAreas(@AuthenticationPrincipal Jwt jwt) {
        return areaService.getAreas(registry.currentUser(jwt));
    }

    /**
     * A single team area the caller may open without a team grant of their own -- as an admin above
     * the team (global, region, club). 403 otherwise, 404 for an unknown team.
     */
    @GetMapping("/me/areas/teams/{teamIdentityId}")
    @PreAuthorize("@authz.canOpenTeamArea(#teamIdentityId)")
    public AreaDto teamArea(@PathVariable String teamIdentityId) {
        return areaService.teamArea(teamIdentityId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Team not found"));
    }

    @GetMapping("/me/roles")
    public List<RoleAssignmentDto> myRoles(@AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.myRoleDtos(registry.currentUser(jwt));
    }
}
