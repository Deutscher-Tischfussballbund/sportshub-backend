package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayConfirmedEvent;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.team.Team;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StandingService {

    private final StandingRepository standingRepository;
    private final GroupRepository groupRepository;
    private final MatchRepository matchRepository;
    private final LeagueRuleResolver ruleResolver;

    public StandingService(StandingRepository standingRepository, GroupRepository groupRepository,
                           MatchRepository matchRepository, LeagueRuleResolver ruleResolver) {
        this.standingRepository = standingRepository;
        this.groupRepository = groupRepository;
        this.matchRepository = matchRepository;
        this.ruleResolver = ruleResolver;
    }

    @Transactional(readOnly = true)
    public List<StandingDto> getByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        return standingRepository.findByGroupOrderByPointsDescSetsWonDesc(group).stream()
            .map(this::toDto)
            .toList();
    }

    @EventListener
    @Transactional
    public void onMatchDayConfirmed(MatchDayConfirmedEvent event) {
        MatchDay matchDay = event.getMatchDay();
        Round round = matchDay.getRound();
        if (round == null) return;
        Group group = round.getGroup();
        if (group == null) return;

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

        // Points come from the group's effective LeagueRuleSet (tier's own, else the league's),
        // falling back to 2/1/0 when no rule set is configured.
        LeagueRuleSet rules = ruleResolver.effectiveFor(group);
        int pointsWin = ruleResolver.pointsWin(rules);
        int pointsDraw = ruleResolver.pointsDraw(rules);
        int pointsLoss = ruleResolver.pointsLoss(rules);

        boolean homeWon = homeWins > awayWins;
        boolean awayWon = awayWins > homeWins;
        boolean isDraw = homeWins == awayWins;

        updateStanding(group, homeTeam, homeWon, isDraw, awayWon, homeSets, awaySets,
            pointsWin, pointsDraw, pointsLoss);
        updateStanding(group, awayTeam, awayWon, isDraw, homeWon, awaySets, homeSets,
            pointsWin, pointsDraw, pointsLoss);
    }

    private void updateStanding(Group group, Team team, boolean won, boolean draw, boolean lost,
                                 int setsFor, int setsAgainst,
                                 int pointsWin, int pointsDraw, int pointsLoss) {
        Standing standing = standingRepository.findByGroupAndTeam(group, team).orElseGet(() -> {
            Standing s = new Standing();
            s.setGroup(group);
            s.setTeam(team);
            return s;
        });

        standing.setPlayed(standing.getPlayed() + 1);
        standing.setSetsWon(standing.getSetsWon() + setsFor);
        standing.setSetsLost(standing.getSetsLost() + setsAgainst);

        if (won) {
            standing.setWins(standing.getWins() + 1);
            standing.setPoints(standing.getPoints() + pointsWin);
        } else if (draw) {
            standing.setDraws(standing.getDraws() + 1);
            standing.setPoints(standing.getPoints() + pointsDraw);
        } else {
            standing.setLosses(standing.getLosses() + 1);
            standing.setPoints(standing.getPoints() + pointsLoss);
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
