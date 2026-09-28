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
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
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
    private final MatchDayRepository matchDayRepository;

    public StandingService(StandingRepository standingRepository, GroupRepository groupRepository,
                           MatchRepository matchRepository, LeagueRuleResolver ruleResolver,
                           MatchDayRepository matchDayRepository) {
        this.standingRepository = standingRepository;
        this.groupRepository = groupRepository;
        this.matchRepository = matchRepository;
        this.ruleResolver = ruleResolver;
        this.matchDayRepository = matchDayRepository;
    }

    @Transactional(readOnly = true)
    public List<StandingDto> getByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        return standingRepository.findByGroupOrderByPointsDescSetsWonDesc(group).stream()
            .map(this::toDto)
            .toList();
    }

    /**
     * A fixture became final (or a final one was corrected by an admin): recompute the group's whole
     * table from all its final fixtures, so a correction replaces the old result instead of counting
     * twice (SPO-73). Points come from the group's effective {@code LeagueRuleSet} (tier's own, else
     * the league's), falling back to 2/1/0 when no rule set is configured.
     */
    @EventListener
    @Transactional
    public void onMatchDayConfirmed(MatchDayConfirmedEvent event) {
        Round round = event.getMatchDay().getRound();
        if (round == null || round.getGroup() == null) return;
        recompute(round.getGroup());
    }

    /** Rebuilds the group's standings from scratch out of its {@code CONFIRMED} fixtures. */
    @Transactional
    public void recompute(Group group) {
        standingRepository.deleteAll(standingRepository.findByGroupOrderByPointsDescSetsWonDesc(group));
        standingRepository.flush();

        LeagueRuleSet rules = ruleResolver.effectiveFor(group);
        int pointsWin = ruleResolver.pointsWin(rules);
        int pointsDraw = ruleResolver.pointsDraw(rules);
        int pointsLoss = ruleResolver.pointsLoss(rules);

        for (MatchDay matchDay : matchDayRepository.findByRoundGroupId(group.getId())) {
            if (matchDay.getResultState() != ResultState.CONFIRMED) continue;

            int homeWins = 0, awayWins = 0;
            int homeSets = 0, awaySets = 0;
            for (Match match : matchRepository.findByMatchDay(matchDay)) {
                Integer home = match.getHomeScore();
                Integer away = match.getAwayScore();
                if (home == null || away == null) continue;
                homeSets += home;
                awaySets += away;
                if (home > away) homeWins++;
                else if (away > home) awayWins++;
            }

            boolean homeWon = homeWins > awayWins;
            boolean awayWon = awayWins > homeWins;
            boolean isDraw = homeWins == awayWins;

            updateStanding(group, matchDay.getTeamHome(), homeWon, isDraw, awayWon, homeSets, awaySets,
                pointsWin, pointsDraw, pointsLoss);
            updateStanding(group, matchDay.getTeamAway(), awayWon, isDraw, homeWon, awaySets, homeSets,
                pointsWin, pointsDraw, pointsLoss);
        }
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
