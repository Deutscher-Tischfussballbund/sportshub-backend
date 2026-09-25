package de.dtfb.sportshub.backend.access.apikey;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A backend-issued, read-only API key for a machine consumer (e.g. a public results site). Only
 * the SHA-256 hash of the key is stored -- the plaintext is shown once at creation. Authenticated
 * requests may only read ({@code GET}/{@code HEAD}), never on {@code /v1/admin/**} or
 * {@code /v1/auth/**}; see {@link ApiKeyAuthenticationFilter} and docs/20-api-keys.md.
 */
@Entity
@Getter
@Setter
public class ApiKey extends BaseEntity {

    /** Human label for the admin UI, e.g. "Public Results Site". */
    @Column(nullable = false)
    private String name;

    /** The first characters of the key, shown in the admin UI so keys can be told apart. */
    @Column(nullable = false)
    private String keyPrefix;

    /** Hex SHA-256 of the full key -- the lookup key; the plaintext is never stored. */
    @Column(nullable = false, unique = true)
    private String keyHash;

    @Column(nullable = false)
    private boolean active = true;

    /** Last day the key is valid (inclusive); null = never expires. */
    private LocalDate expiresAt;

    @Column(nullable = false)
    private Instant createdAt;

    /** dtfb_id of the admin who created the key. */
    private String createdByDtfbId;

    private Instant lastUsedAt;

    /** Usable right now: active and not past its expiry date. */
    public boolean isUsable(LocalDate today) {
        return active && (expiresAt == null || !today.isAfter(expiresAt));
    }
}
