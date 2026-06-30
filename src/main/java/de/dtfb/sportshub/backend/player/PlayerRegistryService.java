package de.dtfb.sportshub.backend.player;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Owns the federation-member directory: resolving (and lazily creating) the player
 * behind a Keycloak identity, plus directory lookups used by the admin surface.
 * The external-API {@code PlayerService} is a separate concern (tournament data).
 */
@Service
public class PlayerRegistryService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;

    public PlayerRegistryService(PlayerRepository playerRepository, PlayerMapper playerMapper) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
    }

    /** The player behind the token, created on first login from {@code dtfb_id}/{@code email}. */
    @Transactional
    public Player currentPlayer(Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        if (dtfbId == null || dtfbId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token missing dtfb_id claim");
        }
        return playerRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            Player player = new Player();
            player.setDtfbId(dtfbId);
            player.setEmail(jwt.getClaimAsString("email"));
            // Lightweight access tokens usually omit profile claims; capture them when present.
            player.setFirstName(jwt.getClaimAsString("given_name"));
            player.setLastName(jwt.getClaimAsString("family_name"));
            player.setActive(true);
            return playerRepository.save(player);
        });
    }

    @Transactional(readOnly = true)
    public List<PlayerDto> findAll() {
        return playerMapper.toDtoList(playerRepository.findAll());
    }

    @Transactional(readOnly = true)
    public List<PlayerDto> search(String q) {
        List<Player> players = (q == null || q.isBlank())
            ? playerRepository.findAll()
            : playerRepository
                .findByFirstNameContainingIgnoreCaseOrLastNameContainingIgnoreCaseOrNationalIdContainingIgnoreCase(q, q, q);
        return playerMapper.toDtoList(players);
    }
}
