package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportSet;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.matchset.MatchSet;
import de.dtfb.sportshub.backend.matchset.MatchSetRepository;
import org.springframework.stereotype.Component;

@Component
public class SetImporter {
    private final MatchSetRepository matchSetRepository;

    public SetImporter(MatchSetRepository matchSetRepository) {
        this.matchSetRepository = matchSetRepository;
    }

    public MatchSet importSet(ImportSet importingSet, Match match) {
        return matchSetRepository
            .findByMatchAndSetNumber(match, importingSet.getNumber())
            .orElseGet(() -> {
                MatchSet ms = new MatchSet();
                ms.setSetNumber(importingSet.getNumber());
                ms.setAwayScore(importingSet.getScoreAway());
                ms.setHomeScore(importingSet.getScoreHome());
                ms.setMatch(match);
                return matchSetRepository.save(ms);
            });
    }
}
