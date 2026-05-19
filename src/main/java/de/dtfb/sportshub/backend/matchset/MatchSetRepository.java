package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MatchSetRepository extends JpaRepository<MatchSet, String> {
    Optional<MatchSet> findByMatchAndSetNumber(Match match, Integer setNumber);
}
