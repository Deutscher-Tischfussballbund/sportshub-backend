package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.pool.Pool;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoundRepository extends JpaRepository<Round, UUID> {
    Optional<Round> findByPoolAndName(Pool pool, String name);
}
