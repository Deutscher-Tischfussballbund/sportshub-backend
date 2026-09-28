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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

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

    /** The official table: only final ({@code CONFIRMED}) fixtures count. Stored, recomputed on every finalization. */
    @Transactional(readOnly = true)
    public List<StandingDto> getByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        return standingRepository.findByGroupOrderByPointsDescSetsWonDesc(group).stream()
            .map(standing -> toDto(standing, false))
            .toList();
    }

    /**
     * The live table: final fixtures plus entered but not yet confirmed ones ({@code SUBMITTED}), with
     * the games scored so far -- so it moves with every game a team enters. Computed on request, never
     * stored. A row is {@code provisional} if at least one of the team's counted fixtures isn't final.
     */
    @Transactional(readOnly = true)
    public List<StandingDto> getProvisionalByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        Set<String> provisionalTeams = new HashSet<>();
        List<Standing> table = tally(group, matchDay -> {
            if (matchDay.getResultState() == ResultState.SUBMITTED) {
                provisionalTeams.add(matchDay.getTeamHome().getId());
                provisionalTeams.add(matchDay.getTeamAway().getId());
                return true;
            }
            return matchDay.getResultState() == ResultState.CONFIRMED;
        });
        return table.stream()
            .sorted(TABLE_ORDER)
            .map(standing -> toDto(standing, provisionalTeams.contains(standing.getTeam().getId())))
            .toList();
    }

    /**
     * A fixture became final (or a final one was corrected by an admin): recompute the group's whole
     * table from all its final fixtures, so a correction replaces the old result instead of counting
     * twice (SPO-73).
     */
    @EventListener
    @Transactional
    public void onMatchDayConfirmed(MatchDayConfirmedEvent event) {
        Round round = event.getMatchDay().getRound();
        if (round == null || round.getGroup() == null) return;
        recompute(round.getGroup());
    }

    /** Rebuilds the group's stored standings from scratch out of its {@code CONFIRMED} fixtures. */
    @Transactional
    public void recompute(Group group) {
        standingRepository.deleteAll(standingRepository.findByGroupOrderByPointsDescSetsWonDesc(group));
        standingRepository.flush();
        standingRepository.saveAll(tally(group, matchDay -> matchDay.getResultState() == ResultState.CONFIRMED));
    }

    /**
     * Tallies the group's table (unsaved rows) over the fixtures {@code counts} accepts. Per fixture,
     * the side with more game wins wins; the game scores add up to sets won/lost. Points come from
     * the group's effective {@code LeagueRuleSet} (tier's own, else the league's), falling back to
     * 2/1/0 when no rule set is configured.
     */
    private List<Standing> tally(Group group, Predicate<MatchDay> counts) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(group);
        int pointsWin = ruleResolver.pointsWin(rules);
        int pointsDraw = ruleResolver.pointsDraw(rules);
        int pointsLoss = ruleResolver.pointsLoss(rules);

        Map<String, Standing> byTeam = new LinkedHashMap<>();
        for (MatchDay matchDay : matchDayRepository.findByRoundGroupId(group.getId())) {
            if (!counts.test(matchDay)) continue;

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

            add(row(byTeam, group, matchDay.getTeamHome()), homeWon, isDraw, homeSets, awaySets,
                pointsWin, pointsDraw, pointsLoss);
            add(row(byTeam, group, matchDay.getTeamAway()), awayWon, isDraw, awaySets, homeSets,
                pointsWin, pointsDraw, pointsLoss);
        }
        return new ArrayList<>(byTeam.values());
    }

    private static Standing row(Map<String, Standing> byTeam, Group group, Team team) {
        return byTeam.computeIfAbsent(team.getId(), id -> {
            Standing s = new Standing();
            s.setGroup(group);
            s.setTeam(team);
            return s;
        });
    }

    private static void add(Standing standing, boolean won, boolean draw, int setsFor, int setsAgainst,
                            int pointsWin, int pointsDraw, int pointsLoss) {
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
    }

    /** Same order as the stored table's query: points, then sets won. */
    private static final Comparator<Standing> TABLE_ORDER =
        Comparator.comparingInt(Standing::getPoints).reversed()
            .thenComparing(Comparator.comparingInt(Standing::getSetsWon).reversed());

    private StandingDto toDto(Standing s, boolean provisional) {
        StandingDto dto = new StandingDto();
        dto.setProvisional(provisional);
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
