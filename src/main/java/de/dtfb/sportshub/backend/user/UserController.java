package de.dtfb.sportshub.backend.user;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        String email = jwt.getClaimAsString("email");
        User user = userService.findOrCreateByDtfbId(dtfbId, email);
        return userService.toDto(user);
    }
}