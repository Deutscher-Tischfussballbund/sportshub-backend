package de.dtfb.sportshub.backend.externalApi;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "external-api")
public class ExternalApi {

    @NotBlank
    private String baseUrl;

    @Min(1)
    private int connectTimeout;
    @Min(1)
    private int readTimeout;
}
