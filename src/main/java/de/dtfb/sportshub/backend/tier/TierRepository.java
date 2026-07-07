package de.dtfb.sportshub.backend.tier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface TierRepository extends JpaRepository<Tier, String> {

    /** Tiers of one competition (copy-forward source walk / structure read). */
    List<Tier> findByCompetitionId(String competitionId);

    @Query("select e from Tier e where e.competition.season.archivedAt is null")
    List<Tier> findAllVisible();

    @Query("select e from Tier e where e.id = :id and e.competition.season.archivedAt is null")
    Optional<Tier> findVisibleById(String id);
}
