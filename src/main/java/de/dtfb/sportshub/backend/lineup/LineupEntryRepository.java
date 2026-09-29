package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.match.Match;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LineupEntryRepository extends JpaRepository<LineupEntry, String> {
    List<LineupEntry> findByLineup(Lineup lineup);

    void deleteByLineup(Lineup lineup);

    List<LineupEntry> findByMatch(Match match);
}
