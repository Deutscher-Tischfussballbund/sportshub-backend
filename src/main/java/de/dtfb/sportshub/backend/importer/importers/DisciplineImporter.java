package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryRepository;
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
    private final CategoryRepository categoryRepository;

    public DisciplineImporter(DisciplineRepository disciplineRepository, StageImporter stageImporter, EntityManager em, CategoryRepository categoryRepository) {
        this.disciplineRepository = disciplineRepository;
        this.stageImporter = stageImporter;
        this.em = em;
        this.categoryRepository = categoryRepository;
    }

    public void importDiscipline(ImportDiscipline importingDiscipline, Event event) {

        Category check = new Category();
        check.setName(importingDiscipline.getName());
        check.setShortName(importingDiscipline.getShortName());
        Discipline discipline = disciplineRepository
            .findByEventAndCategory(event, check)
            .orElseGet(() -> {
                Discipline d = new Discipline();
                d.setEvent(event);
                return disciplineRepository.save(d);
            });

        categoryImporter

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
