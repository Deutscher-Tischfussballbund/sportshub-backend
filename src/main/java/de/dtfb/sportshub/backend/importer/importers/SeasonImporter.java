package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.importer.data.ImportEvent;
import de.dtfb.sportshub.backend.importer.data.ImportSeason;
import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;

@Service
public class SeasonImporter {

    private final SeasonRepository seasonRepository;
    private final EventImporter eventImporter;
    private final EntityManager em;

    public SeasonImporter(SeasonRepository seasonRepository, EventImporter eventImporter, EntityManager em) {
        this.seasonRepository = seasonRepository;
        this.eventImporter = eventImporter;
        this.em = em;
    }

    public void importSeason(ImportSeason importingSeason, Federation federation, String importId) {

        Season season = seasonRepository
            .findByFederationAndName(federation, importingSeason.getName())
            .orElseGet(() -> {
                Season s = new Season();
                s.setName(importingSeason.getName());
                s.setFederation(federation);
                return seasonRepository.save(s);
            });

        int counter = 0;
        for (ImportEvent importingEvent : importingSeason.getEvents()) {
            eventImporter.importEvent(importingEvent, season, importId);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                season = em.merge(season);
            }
        }
        em.flush();
    }
}
