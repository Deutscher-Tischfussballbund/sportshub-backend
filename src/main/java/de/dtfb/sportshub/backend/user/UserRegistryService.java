package de.dtfb.sportshub.backend.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Owns login-identity resolution: the {@link User} behind a Keycloak JWT, created lazily on first
 * login from {@code dtfb_id}/{@code email}/{@code given_name}/{@code family_name}. This is the sole
 * "who is logged in" resolver -- distinct from
 * {@link de.dtfb.sportshub.backend.player.PlayerDirectoryService}, which resolves competitor
 * records, not auth identity.
 */
@Service
public class UserRegistryService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserRegistryService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    @Transactional
    public User currentUser(Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        if (dtfbId == null || dtfbId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing dtfb_id claim");
        }
        return userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User user = new User();
            user.setDtfbId(dtfbId);
            user.setEmail(jwt.getClaimAsString("email"));
            // Lightweight access tokens usually omit profile claims; capture them when present.
            user.setFirstName(jwt.getClaimAsString("given_name"));
            user.setLastName(jwt.getClaimAsString("family_name"));
            return userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public List<UserDto> search(String q) {
        List<User> users = (q == null || q.isBlank())
            ? userRepository.findAll()
            : userRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrEmailContainingIgnoreCase(q, q, q);
        return userMapper.toDtoList(users);
    }
}
