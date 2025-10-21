package de.dtfb.sportshub.backend.player;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/players")
public class PlayerController {
    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
        this.playerService = playerService;
    }

    // ==========================================================
    // ============== READ ENDPOINTS =============================
    // ==========================================================

    @GetMapping
    public List<PlayerDto> allPlayers() {
        return playerService.getAllPlayers();
    }

    @GetMapping("/{id}")
    public PlayerDto playerById(@PathVariable Long id) {
        return playerService.getPlayerById(id);
    }

    // ==========================================================
    // ============== WRITE ENDPOINTS ============================
    // ==========================================================
}
