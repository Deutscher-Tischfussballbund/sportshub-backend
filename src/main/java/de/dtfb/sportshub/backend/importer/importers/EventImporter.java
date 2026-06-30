package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.competition.Competition;
import de.dtfb.sportshub.backend.competition.CompetitionRepository;
import de.dtfb.sportshub.backend.importer.data.ImportDiscipline;
import de.dtfb.sportshub.backend.importer.data.ImportEvent;
import de.dtfb.sportshub.backend.season.Season;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class EventImporter {

    private final CompetitionRepository competitionRepository;
    private final DisciplineImporter disciplineImporter;
    private final EntityManager em;

    public EventImporter(CompetitionRepository competitionRepository, DisciplineImporter disciplineImporter, EntityManager em) {
        this.competitionRepository = competitionRepository;
        this.disciplineImporter = disciplineImporter;
        this.em = em;
    }

    public void importEvent(ImportEvent importingEvent, Season season, String importId) {

        Competition competition = competitionRepository
            .findBySeasonAndName(season, importingEvent.getName())
            .orElseGet(() -> {
                Competition e = new Competition();
                e.setName(importingEvent.getName());
                e.setSeason(season);
                e.setImportId(importId);
                return competitionRepository.save(e);
            });

        int counter = 0;
        for (ImportDiscipline d : importingEvent.getDisciplines()) {
            disciplineImporter.importDiscipline(d, competition);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                competition = em.merge(competition);
            }
        }
        em.flush();
    }
}
