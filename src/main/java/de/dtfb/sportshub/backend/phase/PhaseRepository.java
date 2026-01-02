package de.dtfb.sportshub.backend.phase;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PhaseRepository extends JpaRepository<Phase, Long> {
    Optional<Phase> findByUuid(UUID uuid);
}
