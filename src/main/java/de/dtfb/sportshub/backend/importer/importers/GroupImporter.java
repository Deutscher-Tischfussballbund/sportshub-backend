package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportGroup;
import de.dtfb.sportshub.backend.importer.data.ImportRound;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import de.dtfb.sportshub.backend.stage.Stage;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class GroupImporter {
    private final PoolRepository poolRepository;
    private final RoundImporter roundImporter;
    private final EntityManager em;

    public GroupImporter(PoolRepository poolRepository, RoundImporter roundImporter, EntityManager em) {
        this.poolRepository = poolRepository;
        this.roundImporter = roundImporter;
        this.em = em;
    }

    public void importGroup(ImportGroup importingGroup, Stage stage) {

        Pool pool = poolRepository
            .findByStageAndName(stage, importingGroup.getName())
            .orElseGet(() -> {
                Pool p = new Pool();
                p.setName(importingGroup.getName());
                p.setPoolState(importingGroup.getState());
                p.setTournamentMode(importingGroup.getTournamentMode());
                p.setStage(stage);
                return poolRepository.save(p);
            });

        int counter = 0;
        for (ImportRound r : importingGroup.getRounds()) {
            roundImporter.importRound(r, pool);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                pool = em.merge(pool);
            }
        }
    }
}
