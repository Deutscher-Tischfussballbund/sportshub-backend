package de.dtfb.sportshub.backend.standing;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
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
import de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A group's table (docs/17, docs/22). Official: final ({@code CONFIRMED}) fixtures only. Live
 * ({@code provisional}): also entered, not yet confirmed ones, with what's entered so far. Both are
 * computed from the fixtures on request; the stored {@link Standing} rows are the official table
 * cached for guards (e.g. "has this team a standing in the league"), rebuilt on every finalization.
 *
 * <p>Per fixture: in a {@code RACE} rule set the final running score is the fixture's score (goals for
 * and against); otherwise the side with more game wins wins and the game scores add up. A fixture
 * against the bye is a win for the team sitting out with the rule set's bye score (42 : 30). Points
 * come from the rule set (fallback 2/1/0). Order: points, goal difference, head-to-head (points, then
 * goal difference among the tied teams only), goals for, name -- a tie beyond that (lot/penalty) is
 * resolved by an admin. Every active team placed in the group gets a row, also before its first counted
 * fixture (all zeros) -- in the served tables only; the stored rows keep only teams with a counted
 * fixture, since guards read them as "has recorded results".
 */
@Service
public class StandingService {

    private final StandingRepository standingRepository;
    private final GroupRepository groupRepository;
    private final MatchRepository matchRepository;
    private final LeagueRuleResolver ruleResolver;
    private final MatchDayRepository matchDayRepository;
    private final TeamParticipationRepository participationRepository;

    public StandingService(StandingRepository standingRepository, GroupRepository groupRepository,
                           MatchRepository matchRepository, LeagueRuleResolver ruleResolver,
                           MatchDayRepository matchDayRepository, TeamParticipationRepository participationRepository) {
        this.standingRepository = standingRepository;
        this.groupRepository = groupRepository;
        this.matchRepository = matchRepository;
        this.ruleResolver = ruleResolver;
        this.matchDayRepository = matchDayRepository;
        this.participationRepository = participationRepository;
    }

    /** The official table: only final fixtures count. */
    @Transactional(readOnly = true)
    public List<StandingDto> getByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        return toDtos(compute(group, matchDay -> matchDay.getResultState() == ResultState.CONFIRMED, true), Set.of());
    }

    /**
     * The live table: final fixtures plus entered but not yet confirmed ones ({@code SUBMITTED}), with
     * what's entered so far -- so it moves with every game or segment. A row is {@code provisional} if
     * at least one of the team's counted fixtures isn't final.
     */
    @Transactional(readOnly = true)
    public List<StandingDto> getProvisionalByGroup(String groupId) {
        Group group = groupRepository.findVisibleById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));
        Set<String> provisionalTeams = new HashSet<>();
        Table table = compute(group, matchDay -> {
            if (matchDay.getResultState() == ResultState.SUBMITTED) {
                provisionalTeams.add(matchDay.getTeamHome().getId());
                if (matchDay.getTeamAway() != null) {
                    provisionalTeams.add(matchDay.getTeamAway().getId());
                }
                return true;
            }
            return matchDay.getResultState() == ResultState.CONFIRMED;
        }, true);
        return toDtos(table, provisionalTeams);
    }

    /**
     * A fixture became final (or a final one was corrected by an admin): rebuild the stored table
     * from all final fixtures, so a correction replaces the old result instead of counting twice
     * (SPO-73).
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
        standingRepository.saveAll(compute(group, matchDay -> matchDay.getResultState() == ResultState.CONFIRMED, false).rows());
    }

    /** One counted fixture between two teams, as the head-to-head comparison needs it. */
    private record Outcome(String homeTeamId, String awayTeamId, int homeScore, int awayScore) {
    }

    /** The tallied rows (unsaved, ranked) plus the fixture outcomes they came from. */
    private record Table(List<Standing> rows, List<Outcome> outcomes, int pointsWin, int pointsDraw, int pointsLoss) {
    }

    /** {@code withPlaced}: also a zero row for every active placed team without a counted fixture yet. */
    private Table compute(Group group, Predicate<MatchDay> counts, boolean withPlaced) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(group);
        int pointsWin = ruleResolver.pointsWin(rules);
        int pointsDraw = ruleResolver.pointsDraw(rules);
        int pointsLoss = ruleResolver.pointsLoss(rules);
        boolean race = rules != null && rules.getFixtureMode() == FixtureMode.RACE;

        Map<String, Standing> byTeam = new LinkedHashMap<>();
        List<Outcome> outcomes = new ArrayList<>();
        for (MatchDay matchDay : matchDayRepository.findByRoundGroupId(group.getId())) {
            if (!counts.test(matchDay)) continue;
            // A fixture without its teams (incomplete data) can't be tallied.
            if (matchDay.getTeamHome() == null || (!matchDay.isBye() && matchDay.getTeamAway() == null)) continue;

            if (matchDay.isBye()) {
                int winner = rules != null && rules.getRaceByeScoreWinner() != null ? rules.getRaceByeScoreWinner() : 42;
                int loser = rules != null && rules.getRaceByeScoreLoser() != null ? rules.getRaceByeScoreLoser() : 30;
                add(row(byTeam, group, matchDay.getTeamHome()), true, false, winner, loser, pointsWin, pointsDraw, pointsLoss);
                continue;
            }

            int[] score = race ? raceScore(matchDay) : gameScore(matchDay);
            int homeScore = score[0];
            int awayScore = score[1];
            boolean homeWon = score[2] > score[3];
            boolean awayWon = score[3] > score[2];
            boolean isDraw = !homeWon && !awayWon;

            add(row(byTeam, group, matchDay.getTeamHome()), homeWon, isDraw, homeScore, awayScore,
                pointsWin, pointsDraw, pointsLoss);
            add(row(byTeam, group, matchDay.getTeamAway()), awayWon, isDraw, awayScore, homeScore,
                pointsWin, pointsDraw, pointsLoss);
            outcomes.add(new Outcome(matchDay.getTeamHome().getId(), matchDay.getTeamAway().getId(),
                score[2], score[3]));
        }
        if (withPlaced) {
            for (TeamParticipation placed : participationRepository.findByGroup_IdAndStatus(group.getId(), ParticipationStatus.ACTIVE)) {
                row(byTeam, group, placed.getTeam());
            }
        }
        Table table = new Table(new ArrayList<>(byTeam.values()), outcomes, pointsWin, pointsDraw, pointsLoss);
        table.rows().sort(ranking(table));
        return table;
    }

    /**
     * RACE: the running score of the last entered segment is the fixture's score -- goals for/against
     * and the winner alike. Returns {home goals, away goals, home "wins", away "wins"}.
     */
    private int[] raceScore(MatchDay matchDay) {
        int home = 0;
        int away = 0;
        List<Match> segments = new ArrayList<>(matchRepository.findByMatchDay(matchDay));
        segments.sort(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())));
        for (Match segment : segments) {
            if (segment.getHomeScore() == null || segment.getAwayScore() == null) break;
            home = segment.getHomeScore();
            away = segment.getAwayScore();
        }
        return new int[] {home, away, home, away};
    }

    /** GAMES: game scores add up; the side with more game wins wins. */
    private int[] gameScore(MatchDay matchDay) {
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
        return new int[] {homeSets, awaySets, homeWins, awayWins};
    }

    /** Points, goal difference, head-to-head among the tied teams, goals for, name. */
    private static Comparator<Standing> ranking(Table table) {
        Comparator<Standing> primary = Comparator
            .comparingInt(Standing::getPoints).reversed()
            .thenComparing(Comparator.comparingInt(StandingService::difference).reversed());
        return primary
            .thenComparing((a, b) -> headToHead(table, primary, a, b))
            .thenComparing(Comparator.comparingInt(Standing::getSetsWon).reversed())
            .thenComparing(s -> Objects.requireNonNullElse(s.getTeam().getName(), ""));
    }

    /**
     * Compares two rows that are level on points and goal difference by a mini-table of the fixtures
     * among all teams level with them: points first, then goal difference.
     */
    private static int headToHead(Table table, Comparator<Standing> primary, Standing a, Standing b) {
        Set<String> tied = new HashSet<>();
        for (Standing row : table.rows()) {
            if (primary.compare(row, a) == 0) {
                tied.add(row.getTeam().getId());
            }
        }
        Map<String, int[]> mini = new HashMap<>(); // teamId -> {points, goals for, goals against}
        for (Outcome o : table.outcomes()) {
            if (!tied.contains(o.homeTeamId()) || !tied.contains(o.awayTeamId())) continue;
            int[] home = mini.computeIfAbsent(o.homeTeamId(), id -> new int[3]);
            int[] away = mini.computeIfAbsent(o.awayTeamId(), id -> new int[3]);
            home[1] += o.homeScore();
            home[2] += o.awayScore();
            away[1] += o.awayScore();
            away[2] += o.homeScore();
            if (o.homeScore() > o.awayScore()) {
                home[0] += table.pointsWin();
                away[0] += table.pointsLoss();
            } else if (o.awayScore() > o.homeScore()) {
                away[0] += table.pointsWin();
                home[0] += table.pointsLoss();
            } else {
                home[0] += table.pointsDraw();
                away[0] += table.pointsDraw();
            }
        }
        int[] ma = mini.getOrDefault(a.getTeam().getId(), new int[3]);
        int[] mb = mini.getOrDefault(b.getTeam().getId(), new int[3]);
        if (ma[0] != mb[0]) return Integer.compare(mb[0], ma[0]);
        return Integer.compare(mb[1] - mb[2], ma[1] - ma[2]);
    }

    private static int difference(Standing s) {
        return s.getSetsWon() - s.getSetsLost();
    }

    private static Standing row(Map<String, Standing> byTeam, Group group, Team team) {
        return byTeam.computeIfAbsent(team.getId(), id -> {
            Standing s = new Standing();
            s.setGroup(group);
            s.setTeam(team);
            return s;
        });
    }

    private static void add(Standing standing, boolean won, boolean draw, int scoreFor, int scoreAgainst,
                            int pointsWin, int pointsDraw, int pointsLoss) {
        standing.setPlayed(standing.getPlayed() + 1);
        standing.setSetsWon(standing.getSetsWon() + scoreFor);
        standing.setSetsLost(standing.getSetsLost() + scoreAgainst);

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

    private List<StandingDto> toDtos(Table table, Set<String> provisionalTeams) {
        List<StandingDto> dtos = new ArrayList<>();
        for (int i = 0; i < table.rows().size(); i++) {
            Standing s = table.rows().get(i);
            StandingDto dto = new StandingDto();
            dto.setPlace(i + 1);
            dto.setProvisional(provisionalTeams.contains(s.getTeam().getId()));
            dto.setTeamId(s.getTeam().getId());
            dto.setTeamName(s.getTeam().getName());
            dto.setPlayed(s.getPlayed());
            dto.setWins(s.getWins());
            dto.setDraws(s.getDraws());
            dto.setLosses(s.getLosses());
            dto.setPoints(s.getPoints());
            dto.setSetsWon(s.getSetsWon());
            dto.setSetsLost(s.getSetsLost());
            dto.setSetDifference(difference(s));
            dto.setGoalsFor(s.getSetsWon());
            dto.setGoalsAgainst(s.getSetsLost());
            dto.setGoalDifference(difference(s));
            dtos.add(dto);
        }
        return dtos;
    }
}
