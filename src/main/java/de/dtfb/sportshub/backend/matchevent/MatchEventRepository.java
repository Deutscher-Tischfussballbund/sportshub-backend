package de.dtfb.sportshub.backend.matchevent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MatchEventRepository extends JpaRepository<MatchEvent, String> {

    @Query("select e from MatchEvent e where e.match.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    List<MatchEvent> findAllVisible();

    @Query("select e from MatchEvent e where e.id = :id and e.match.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    Optional<MatchEvent> findVisibleById(String id);
}
