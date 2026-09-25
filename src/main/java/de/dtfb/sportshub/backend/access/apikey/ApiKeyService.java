package de.dtfb.sportshub.backend.access.apikey;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Issues and verifies backend-held API keys. A key is {@code dtfb_} + 32 random bytes (base64url);
 * only its SHA-256 hash is stored -- a fast hash is enough because the key is high-entropy random,
 * not a user-chosen password. The plaintext leaves the backend exactly once, in the create response.
 */
@Service
public class ApiKeyService {

    static final String KEY_PREFIX = "dtfb_";
    private static final int PREFIX_SHOWN = KEY_PREFIX.length() + 6;
    /** lastUsedAt is refreshed at most this often, so read-heavy consumers don't write per request. */
    private static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);

    private final ApiKeyRepository repository;
    private final ApiKeyMapper mapper;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository, ApiKeyMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional
    public ApiKeyCreatedDto create(ApiKeyDto dto, String createdByDtfbId) {
        String name = requireName(dto);
        requireNotPast(dto.getExpiresAt());

        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String key = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        ApiKey apiKey = new ApiKey();
        apiKey.setName(name);
        apiKey.setKeyPrefix(key.substring(0, PREFIX_SHOWN));
        apiKey.setKeyHash(hash(key));
        apiKey.setExpiresAt(dto.getExpiresAt());
        apiKey.setActive(true);
        apiKey.setCreatedAt(Instant.now());
        apiKey.setCreatedByDtfbId(createdByDtfbId);
        return new ApiKeyCreatedDto(mapper.toDto(repository.save(apiKey)), key);
    }

    /** Only name, active and expiry are editable -- the key itself can't be changed, only replaced. */
    @Transactional
    public ApiKeyDto update(String id, ApiKeyDto dto) {
        ApiKey apiKey = getApiKey(id);
        apiKey.setName(requireName(dto));
        apiKey.setActive(dto.isActive());
        if (dto.getExpiresAt() != null && !dto.getExpiresAt().equals(apiKey.getExpiresAt())) {
            requireNotPast(dto.getExpiresAt());
        }
        apiKey.setExpiresAt(dto.getExpiresAt());
        return mapper.toDto(repository.save(apiKey));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getApiKey(id));
    }

    /** The usable key matching {@code rawKey}, touching its lastUsedAt; empty if unknown, inactive or expired. */
    @Transactional
    public Optional<ApiKey> authenticate(String rawKey) {
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        Optional<ApiKey> match = repository.findByKeyHash(hash(rawKey))
            .filter(apiKey -> apiKey.isUsable(LocalDate.now()));
        match.ifPresent(apiKey -> {
            Instant now = Instant.now();
            if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_RESOLUTION))) {
                apiKey.setLastUsedAt(now);
            }
        });
        return match;
    }

    static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String requireName(ApiKeyDto dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }
        return name;
    }

    private static void requireNotPast(LocalDate expiresAt) {
        if (expiresAt != null && expiresAt.isBefore(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "expiresAt must not be in the past");
        }
    }

    private @NonNull ApiKey getApiKey(String id) {
        return repository.findById(id).orElseThrow(() -> new ApiKeyNotFoundException(id));
    }
}
