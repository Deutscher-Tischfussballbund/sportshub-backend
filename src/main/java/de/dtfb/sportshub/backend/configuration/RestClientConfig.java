package de.dtfb.sportshub.backend.configuration;

import de.dtfb.sportshub.backend.externalApi.ExternalApi;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ExternalApi.class)
public class RestClientConfig {
    @Bean
    RestClient externalApiRestClient(ExternalApi externalApi) {
        
        return RestClient.builder()
            .baseUrl(externalApi.getBaseUrl())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
}
