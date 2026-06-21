package de.dtfb.sportshub.backend.access.auth;

import de.dtfb.sportshub.backend.access.area.AreaService;
import de.dtfb.sportshub.backend.access.area.MeAreasResponseDto;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAdminService;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentDto;
import de.dtfb.sportshub.backend.player.Player;
import de.dtfb.sportshub.backend.player.PlayerMapper;
import de.dtfb.sportshub.backend.player.PlayerRegistryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Identity surface consumed by the admin frontend's AuthService/AreaService.
 * Every call resolves (and lazily persists) the player behind the bearer token.
 */
@RestController
@RequestMapping("/v1/auth")
public class AuthMeController {

    private final PlayerRegistryService registry;
    private final RoleAdminService roleAdminService;
    private final AreaService areaService;
    private final PlayerMapper playerMapper;

    public AuthMeController(PlayerRegistryService registry,
                           RoleAdminService roleAdminService,
                           AreaService areaService,
                           PlayerMapper playerMapper) {
        this.registry = registry;
        this.roleAdminService = roleAdminService;
        this.areaService = areaService;
        this.playerMapper = playerMapper;
    }

    @GetMapping("/me")
    public MeResponseDto me(@AuthenticationPrincipal Jwt jwt) {
        Player player = registry.currentPlayer(jwt);
        return new MeResponseDto(playerMapper.toDto(player), roleAdminService.myRoleDtos(player));
    }

    @GetMapping("/me/areas")
    public MeAreasResponseDto myAreas(@AuthenticationPrincipal Jwt jwt) {
        return areaService.getAreas(registry.currentPlayer(jwt));
    }

    @GetMapping("/me/roles")
    public List<RoleAssignmentDto> myRoles(@AuthenticationPrincipal Jwt jwt) {
        return roleAdminService.myRoleDtos(registry.currentPlayer(jwt));
    }
}
