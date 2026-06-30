package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.federation.Federation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SeasonRepository extends JpaRepository<Season, String> {
    Optional<Season> findByFederationAndName(Federation federation, String name);

    /** Active (non-archived) seasons. */
    List<Season> findByArchivedAtIsNull();

    /** Archived (soft-deleted) seasons. */
    List<Season> findByArchivedAtIsNotNull();
}
