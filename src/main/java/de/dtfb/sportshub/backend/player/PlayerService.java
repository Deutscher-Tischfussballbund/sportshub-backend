package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.externalApi.ExternalApiClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PlayerService {

    private final ExternalApiClient client;

    public PlayerService(ExternalApiClient client) {
        this.client = client;
    }

    PlayerDto get(String uuid) {
        return client.fetchById(UUID.fromString(uuid));
    }
}
