package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.event.Event;
import de.dtfb.sportshub.backend.event.EventRepository;
import de.dtfb.sportshub.backend.importer.data.ImportDiscipline;
import de.dtfb.sportshub.backend.importer.data.ImportEvent;
import de.dtfb.sportshub.backend.season.Season;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class EventImporter {

    private final EventRepository eventRepository;
    private final DisciplineImporter disciplineImporter;
    private final EntityManager em;

    public EventImporter(EventRepository eventRepository, DisciplineImporter disciplineImporter, EntityManager em) {
        this.eventRepository = eventRepository;
        this.disciplineImporter = disciplineImporter;
        this.em = em;
    }

    public void importEvent(ImportEvent importingEvent, Season season) {

        Event event = eventRepository
            .findBySeasonAndName(season, importingEvent.getName())
            .orElseGet(() -> {
                Event e = new Event();
                e.setName(importingEvent.getName());
                e.setSeason(season);
                return eventRepository.save(e);
            });

        int counter = 0;
        for (ImportDiscipline d : importingEvent.getDisciplines()) {
            disciplineImporter.importDiscipline(d, event);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                event = em.merge(event);
            }
        }
    }
}
