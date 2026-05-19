package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportMatch;
import de.dtfb.sportshub.backend.importer.data.ImportMatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class MatchdayImporter {
    private final MatchDayRepository matchdayRepository;
    private final MatchImporter matchImporter;
    private final TeamImporter teamImporter;
    private final EntityManager em;
    private static final Instant UNKNOWN_DATE_TIME = Instant.EPOCH;

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
                return md;
            });

        matchDay.setStartDate(importingMatchday.getStartDate() == null ? UNKNOWN_DATE_TIME : importingMatchday.getStartDate());
        matchDay.setEndDate(importingMatchday.getEndDate() == null ? UNKNOWN_DATE_TIME : importingMatchday.getEndDate());

        Team homeTeam = teamImporter.importTeam(importingMatchday.getTeamHome());
        Team awayTeam = teamImporter.importTeam(importingMatchday.getTeamAway());
        matchDay.setTeamHome(homeTeam);
        matchDay.setTeamAway(awayTeam);
        matchDay.setRound(round);

        matchdayRepository.save(matchDay);

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
        em.flush();
    }
}
