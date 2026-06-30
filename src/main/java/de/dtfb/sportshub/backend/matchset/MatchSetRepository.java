package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MatchSetRepository extends JpaRepository<MatchSet, String> {
    Optional<MatchSet> findByMatchAndSetNumber(Match match, Integer setNumber);

    @Query("select e from MatchSet e where e.match.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    List<MatchSet> findAllVisible();

    @Query("select e from MatchSet e where e.id = :id and e.match.matchDay.round.pool.stage.discipline.competition.season.archivedAt is null")
    Optional<MatchSet> findVisibleById(String id);
}
