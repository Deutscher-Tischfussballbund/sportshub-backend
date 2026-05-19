package de.dtfb.sportshub.backend.federation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FederationRepository extends JpaRepository<Federation, String> {
    Optional<Federation> findByName(String organisation);
}
