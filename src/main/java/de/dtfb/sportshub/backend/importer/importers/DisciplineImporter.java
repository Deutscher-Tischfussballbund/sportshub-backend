package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.category.Category;
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
    private final CategoryImporter categoryImporter;

    public DisciplineImporter(DisciplineRepository disciplineRepository, StageImporter stageImporter, EntityManager em, CategoryImporter categoryImporter) {
        this.disciplineRepository = disciplineRepository;
        this.stageImporter = stageImporter;
        this.em = em;
        this.categoryImporter = categoryImporter;
    }

    public void importDiscipline(ImportDiscipline importingDiscipline, Event event) {

        Category category = categoryImporter.importCategory(importingDiscipline);

        Discipline discipline = disciplineRepository
            .findByEventAndCategory(event, category)
            .orElseGet(() -> {
                Discipline d = new Discipline();
                d.setEvent(event);
                d.setCategory(category);
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
        em.flush();
    }
}
