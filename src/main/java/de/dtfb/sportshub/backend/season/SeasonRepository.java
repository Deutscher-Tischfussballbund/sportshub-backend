package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.federation.Federation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, String> {
    Optional<Season> findByFederationAndName(Federation federation, String name);
}
