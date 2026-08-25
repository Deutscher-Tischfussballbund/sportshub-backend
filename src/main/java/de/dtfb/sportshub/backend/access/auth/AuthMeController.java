package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.area.AreaService;
import de.dtfb.sportshub.backend.access.area.MeAreasResponseDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAdminService;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentDto;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserMapper;
import de.dtfb.sportshub.backend.user.UserRegistryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/me/roles")
    public List<RoleAssignmentDto> myRoles(@AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.myRoleDtos(registry.currentUser(jwt));
    }
}
