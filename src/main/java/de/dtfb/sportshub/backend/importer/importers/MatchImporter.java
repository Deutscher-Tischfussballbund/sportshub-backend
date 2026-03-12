package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportMatch;
import de.dtfb.sportshub.backend.importer.data.ImportSet;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchset.MatchSet;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class MatchImporter {
    private final MatchRepository matchRepository;
    private final SetImporter setImporter;
    private final EntityManager em;

    public MatchImporter(MatchRepository matchRepository, SetImporter setImporter, EntityManager em) {
        this.matchRepository = matchRepository;
        this.setImporter = setImporter;
        this.em = em;
    }

    public void importMatch(ImportMatch importingMatch, MatchDay matchDay) {
        Match match = matchRepository
            .findByMatchDayAndEndTime(matchDay, importingMatch.getEndDate())
            .orElseGet(() -> {
                Match m = new Match();
                m.setMatchDay(matchDay);
                return matchRepository.save(m);
            });

        match.setType(importingMatch.getType());
        match.setState(importingMatch.getState());
        match.setStartTime(importingMatch.getStartDate());
        match.setEndTime(importingMatch.getEndDate());

        List<MatchSet> matchSets = new ArrayList<>();
        int counter = 0;
        for (ImportSet s : importingMatch.getSets()) {
            matchSets.add(setImporter.importSet(s, match));
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                match = em.merge(match);
            }
        }
        int homeScoreTotal = matchSets.stream().mapToInt(MatchSet::getHomeScore).sum();
        int awayScoreTotal = matchSets.stream().mapToInt(MatchSet::getAwayScore).sum();
        match.setHomeScore(homeScoreTotal);
        match.setAwayScore(awayScoreTotal);
    }
}
