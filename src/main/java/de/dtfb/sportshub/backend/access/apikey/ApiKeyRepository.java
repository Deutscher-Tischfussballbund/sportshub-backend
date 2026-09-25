package de.dtfb.sportshub.backend.access.apikey;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, String> {
    Optional<ApiKey> findByKeyHash(String keyHash);
}
