package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import de.dtfb.sportshub.backend.event.Event;
import de.dtfb.sportshub.backend.importer.data.ImportDiscipline;
import de.dtfb.sportshub.backend.importer.data.ImportStage;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class DisciplineImporter {

    private final DisciplineRepository disciplineRepository;
    private final StageImporter stageImporter;
    private final EntityManager em;

    public DisciplineImporter(DisciplineRepository disciplineRepository, StageImporter stageImporter, EntityManager em) {
        this.disciplineRepository = disciplineRepository;
        this.stageImporter = stageImporter;
        this.em = em;
    }

    public void importDiscipline(ImportDiscipline importingDiscipline, Event event) {

        Discipline discipline = disciplineRepository
            .findByEventAndName(event, importingDiscipline.getName())
            .orElseGet(() -> {
                Discipline d = new Discipline();
                d.setName(importingDiscipline.getName());
                d.setShortName(importingDiscipline.getShortName());
                d.setEvent(event);
                return disciplineRepository.save(d);
            });

        int counter = 0;
        for (ImportStage s : importingDiscipline.getStages()) {
            stageImporter.importStage(s, discipline);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                discipline = em.merge(discipline);
            }
        }
    }
}
