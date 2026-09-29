package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LineupRepository extends JpaRepository<Lineup, String> {
    List<Lineup> findByMatchDay(MatchDay matchDay);

    Optional<Lineup> findByMatchDayAndTeamId(MatchDay matchDay, String teamId);

    /** The line-ups of many fixtures in one query (line-up status in lists). */
    List<Lineup> findByMatchDayIn(Collection<MatchDay> matchDays);
}
