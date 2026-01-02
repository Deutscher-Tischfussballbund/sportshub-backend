package de.dtfb.sportshub.backend.matchset;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MatchsetRepository extends JpaRepository<Matchset, Long> {
    Optional<Matchset> findByUuid(UUID uuid);
}
