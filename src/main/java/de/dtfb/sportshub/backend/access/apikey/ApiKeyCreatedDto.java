package de.dtfb.sportshub.backend.access.apikey;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Response to creating a key: the only time the plaintext {@code key} is ever returned. */
@Getter
@AllArgsConstructor
public class ApiKeyCreatedDto {
    private ApiKeyDto apiKey;
    private String key;
}
