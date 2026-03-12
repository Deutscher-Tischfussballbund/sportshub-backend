package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportMatch;
import de.dtfb.sportshub.backend.importer.data.ImportMatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
public class MatchdayImporter {
    private final MatchDayRepository matchdayRepository;
    private final MatchImporter matchImporter;
    private final TeamImporter teamImporter;
    private final EntityManager em;

    public MatchdayImporter(MatchDayRepository matchdayRepository, MatchImporter matchImporter, TeamImporter teamImporter, EntityManager em) {
        this.matchdayRepository = matchdayRepository;
        this.matchImporter = matchImporter;
        this.teamImporter = teamImporter;
        this.em = em;
    }

    public void importMatchday(ImportMatchDay importingMatchday, Round round) {
        MatchDay matchDay = matchdayRepository
            .findByRoundAndName(round, importingMatchday.getName())
            .orElseGet(() -> {
                MatchDay md = new MatchDay();
                md.setName(importingMatchday.getName());
                return matchdayRepository.save(md);
            });

        matchDay.setStartDate(importingMatchday.getStartDate());
        matchDay.setEndDate(importingMatchday.getEndDate());

        Team homeTeam = teamImporter.importTeam(importingMatchday.getTeamHome());
        Team awayTeam = teamImporter.importTeam(importingMatchday.getTeamAway());
        matchDay.setTeamHome(homeTeam);
        matchDay.setTeamAway(awayTeam);
        matchDay.setRound(round);

        int counter = 0;
        for (ImportMatch m : importingMatchday.getMatches()) {
            matchImporter.importMatch(m, matchDay);
            counter++;

            // batch flush every 50 events
            if (counter % 50 == 0) {
                em.flush();
                em.clear();

                // reattach parent entity after clearing
                matchDay = em.merge(matchDay);
            }
        }
    }
}
