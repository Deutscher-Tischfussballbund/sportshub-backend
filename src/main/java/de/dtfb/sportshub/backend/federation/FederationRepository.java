package de.dtfb.sportshub.backend.federation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FederationRepository extends JpaRepository<Federation, UUID> {
    Optional<Federation> findByName(String organisation);
}
