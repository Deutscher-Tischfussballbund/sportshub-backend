package de.dtfb.sportshub.backend.player;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin-frontend player directory read (separate root path from the external-API {@link PlayerController}). */
@RestController
public class PlayerAdminController {

    private final PlayerRegistryService registry;

    public PlayerAdminController(PlayerRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping("/v1/admin/players")
    public List<PlayerDto> players() {
        return registry.findAll();
    }
}
