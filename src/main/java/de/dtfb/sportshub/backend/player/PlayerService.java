package de.dtfb.sportshub.backend.player;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
@Slf4j
public class PlayerService {
    public static final String PLAYERS = "/players";
    private final RestTemplate restTemplate;

    @Value("${dtfb.player.api}")
    private String baseUrl;

    public PlayerService() {
        this.restTemplate = new RestTemplate();
    }

    public List<PlayerDto> getAllPlayers() {
        ResponseEntity<List<PlayerDto>> response = restTemplate.exchange(
            baseUrl + PLAYERS,
            HttpMethod.GET,
            null, // no request body needed for GET
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody();
    }

    public List<PlayerDto> getPlayersByTeam(Long teamId) { // TODO check if needed later
        ResponseEntity<List<PlayerDto>> response = restTemplate.exchange(
            baseUrl + "/players/" + teamId,
            HttpMethod.GET,
            null, // no request body needed for GET
            new ParameterizedTypeReference<>() {
            }
        );
        return response.getBody();
    }

    public PlayerDto getPlayerById(Long id) {
        ResponseEntity<PlayerDto> response = restTemplate.getForEntity(baseUrl + PLAYERS + "/" + id, PlayerDto.class);
        return response.getBody();
    }
}
