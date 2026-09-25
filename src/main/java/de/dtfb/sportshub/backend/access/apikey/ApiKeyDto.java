package de.dtfb.sportshub.backend.access.apikey;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/** An API key as listed in the admin UI -- never carries the key itself (see {@link ApiKeyCreatedDto}). */
@Getter
@Setter
public class ApiKeyDto {
    private String id;
    private String name;
    private String keyPrefix;
    private boolean active = true;
    private LocalDate expiresAt;
    private Instant createdAt;
    private String createdByDtfbId;
    private Instant lastUsedAt;
}
