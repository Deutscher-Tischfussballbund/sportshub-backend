package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportMatchDay;
import de.dtfb.sportshub.backend.importer.data.ImportRound;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class RoundImporter {
    private final RoundRepository roundRepository;
    private final MatchdayImporter matchdayImporter;
    private final EntityManager em;

    public RoundImporter(RoundRepository roundRepository, MatchdayImporter matchdayImporter, EntityManager em) {
        this.roundRepository = roundRepository;
        this.matchdayImporter = matchdayImporter;
        this.em = em;
    }

    public void importRound(ImportRound importingRound, Pool pool) {

        Round round = roundRepository
            .findByPoolAndName(pool, importingRound.getName())
            .orElseGet(() -> {
                Round r = new Round();
                r.setName(importingRound.getName());
                r.setIndex(importingRound.getIndex());
                r.setPool(pool);
                return roundRepository.save(r);
            });

        int counter = 0;
        for (ImportMatchDay md : importingRound.getMatchdays()) {
            matchdayImporter.importMatchday(md, round);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                round = em.merge(round);
            }
        }
    }
}
