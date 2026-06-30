package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.round.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MatchDayRepository extends JpaRepository<MatchDay, String> {
    Optional<MatchDay> findByRoundAndName(Round round, String name);

    @Query("select e from MatchDay e where e.round.pool.stage.discipline.competition.season.archivedAt is null")
    List<MatchDay> findAllVisible();

    @Query("select e from MatchDay e where e.id = :id and e.round.pool.stage.discipline.competition.season.archivedAt is null")
    Optional<MatchDay> findVisibleById(String id);
}
