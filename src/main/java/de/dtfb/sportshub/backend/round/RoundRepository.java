package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.pool.Pool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RoundRepository extends JpaRepository<Round, String> {
    Optional<Round> findByPoolAndName(Pool pool, String name);

    @Query("select e from Round e where e.pool.stage.discipline.competition.season.archivedAt is null")
    List<Round> findAllVisible();

    @Query("select e from Round e where e.id = :id and e.pool.stage.discipline.competition.season.archivedAt is null")
    Optional<Round> findVisibleById(String id);
}
