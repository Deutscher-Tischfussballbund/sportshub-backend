package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, String> {
    Optional<Match> findByMatchDayAndEndTime(MatchDay matchDay, Instant endTime);
    List<Match> findByMatchDay(MatchDay matchDay);

    @Query("select e from Match e where e.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    List<Match> findAllVisible();

    @Query("select e from Match e where e.id = :id and e.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    Optional<Match> findVisibleById(String id);
}
