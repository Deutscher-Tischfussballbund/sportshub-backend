package de.dtfb.sportshub.backend.pool;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PoolRepository extends JpaRepository<Pool, Long> {
    Optional<Pool> findByUuid(UUID uuid);
}
