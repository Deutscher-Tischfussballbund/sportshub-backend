package de.dtfb.sportshub.backend.player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerMapper playerMapper;

    public PlayerService(PlayerRepository playerRepository, PlayerMapper playerMapper) {
        this.playerRepository = playerRepository;
        this.playerMapper = playerMapper;
    }

    @Transactional(readOnly = true)
    public PlayerDto get(String id) {
        return playerRepository.findById(id)
            .map(playerMapper::toDto)
            .orElseThrow(() -> new PlayerNotFoundException(id));
    }
}
