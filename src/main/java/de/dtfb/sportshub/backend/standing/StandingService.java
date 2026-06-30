package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayConfirmedEvent;
import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolNotFoundException;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StandingService {

    private final StandingRepository standingRepository;
    private final PoolRepository poolRepository;
    private final MatchRepository matchRepository;

    public StandingService(StandingRepository standingRepository, PoolRepository poolRepository,
                           MatchRepository matchRepository) {
        this.standingRepository = standingRepository;
        this.poolRepository = poolRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional(readOnly = true)
    public List<StandingDto> getByPool(String poolId) {
        Pool pool = poolRepository.findVisibleById(poolId)
            .orElseThrow(() -> new PoolNotFoundException(poolId));
        return standingRepository.findByPoolOrderByPointsDescSetsWonDesc(pool).stream()
            .map(this::toDto)
            .toList();
    }

    @EventListener
    @Transactional
    public void onMatchDayConfirmed(MatchDayConfirmedEvent event) {
        MatchDay matchDay = event.getMatchDay();
        Round round = matchDay.getRound();
        if (round == null) return;
        Pool pool = round.getPool();
        if (pool == null) return;

        List<Match> matches = matchRepository.findByMatchDay(matchDay);

        int homeWins = 0, awayWins = 0;
        int homeSets = 0, awaySets = 0;

        for (Match match : matches) {
            Integer home = match.getHomeScore();
            Integer away = match.getAwayScore();
            if (home == null || away == null) continue;
            homeSets += home;
            awaySets += away;
            if (home > away) homeWins++;
            else if (away > home) awayWins++;
        }

        Team homeTeam = matchDay.getTeamHome();
        Team awayTeam = matchDay.getTeamAway();

        // 2 points for win, 1 for draw
        boolean homeWon = homeWins > awayWins;
        boolean awayWon = awayWins > homeWins;
        boolean isDraw = homeWins == awayWins;

        updateStanding(pool, homeTeam, homeWon, isDraw, awayWon, homeSets, awaySets);
        updateStanding(pool, awayTeam, awayWon, isDraw, homeWon, awaySets, homeSets);
    }

    private void updateStanding(Pool pool, Team team, boolean won, boolean draw, boolean lost,
                                 int setsFor, int setsAgainst) {
        Standing standing = standingRepository.findByPoolAndTeam(pool, team).orElseGet(() -> {
            Standing s = new Standing();
            s.setPool(pool);
            s.setTeam(team);
            return s;
        });

        standing.setPlayed(standing.getPlayed() + 1);
        standing.setSetsWon(standing.getSetsWon() + setsFor);
        standing.setSetsLost(standing.getSetsLost() + setsAgainst);

        if (won) {
            standing.setWins(standing.getWins() + 1);
            standing.setPoints(standing.getPoints() + 2);
        } else if (draw) {
            standing.setDraws(standing.getDraws() + 1);
            standing.setPoints(standing.getPoints() + 1);
        } else {
            standing.setLosses(standing.getLosses() + 1);
        }

        standingRepository.save(standing);
    }

    private StandingDto toDto(Standing s) {
        StandingDto dto = new StandingDto();
        dto.setTeamId(s.getTeam().getId());
        dto.setTeamName(s.getTeam().getName());
        dto.setPlayed(s.getPlayed());
        dto.setWins(s.getWins());
        dto.setDraws(s.getDraws());
        dto.setLosses(s.getLosses());
        dto.setPoints(s.getPoints());
        dto.setSetsWon(s.getSetsWon());
        dto.setSetsLost(s.getSetsLost());
        dto.setSetDifference(s.getSetsWon() - s.getSetsLost());
        return dto;
    }
}
