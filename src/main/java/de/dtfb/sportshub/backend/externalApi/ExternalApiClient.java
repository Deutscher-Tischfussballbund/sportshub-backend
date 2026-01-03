package de.dtfb.sportshub.backend.externalApi;

import de.dtfb.sportshub.backend.player.PlayerDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ExternalApiClient {
    private final RestClient restClient;

    public ExternalApiClient(@Qualifier("externalApiRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public PlayerDto fetchById(UUID id) {
        return restClient.get()
            .uri("/players/{id}", id)
            .retrieve()
            .onStatus(HttpStatus.NOT_FOUND::equals,
                (_, _) -> {
                    throw new ExternalResourceNotFoundException();
                }
            )
            .onStatus(HttpStatusCode::is4xxClientError,
                (_, _) -> {
                    throw new ExternalApiException("External api rejected request");
                }
            )
            .onStatus(HttpStatusCode::is5xxServerError,
                (_, _) -> {
                    throw new ExternalApiUnavailableException();
                }
            )
            .body(PlayerDto.class);
    }
}
