package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.importer.data.ImportGroup;
import de.dtfb.sportshub.backend.importer.data.ImportStage;
import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class StageImporter {
    private final StageRepository stageRepository;
    private final GroupImporter groupImporter;
    private final EntityManager em;

    public StageImporter(StageRepository stageRepository, GroupImporter groupImporter, EntityManager em) {
        this.stageRepository = stageRepository;
        this.groupImporter = groupImporter;
        this.em = em;
    }

    public void importStage(ImportStage importingStage, Discipline discipline) {
        Stage stage = stageRepository
            .findByDisciplineAndName(discipline, importingStage.getName())
            .orElseGet(() -> {
                Stage s = new Stage();
                s.setName(importingStage.getName());
                s.setDiscipline(discipline);
                return stageRepository.save(s);
            });

        int counter = 0;
        for (ImportGroup g : importingStage.getGroups()) {
            groupImporter.importGroup(g, stage);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                stage = em.merge(stage);
            }
        }
        em.flush();
    }
}
