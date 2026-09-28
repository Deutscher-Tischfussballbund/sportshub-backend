package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The score of a fixture as the lists show it (docs/22): in a {@code RACE} rule set the running score
 * of the last entered segment (42 : 38, or 18 : 15 while it runs), otherwise the games won (2 : 1); a
 * bye fixture shows the rule set's bye score (42 : 30). No score while nothing is entered. Scores many
 * fixtures at once, with one query for all their games.
 */
@Service
public class FixtureScoreService {

    /** home/away null = nothing entered yet; games entered / total for "(3/7)". */
    public record FixtureScore(Integer home, Integer away, int gamesEntered, int gamesTotal) {
    }

    private final MatchRepository matchRepository;
    private final LeagueRuleResolver ruleResolver;

    public FixtureScoreService(MatchRepository matchRepository, LeagueRuleResolver ruleResolver) {
        this.matchRepository = matchRepository;
        this.ruleResolver = ruleResolver;
    }

    @Transactional(readOnly = true)
    public Map<String, FixtureScore> scores(Collection<MatchDay> matchDays) {
        Map<String, List<Match>> gamesByFixture = matchDays.isEmpty() ? Map.of()
            : matchRepository.findByMatchDayIn(matchDays).stream()
                .collect(Collectors.groupingBy(match -> match.getMatchDay().getId()));
        Map<String, LeagueRuleSet> rulesByGroup = new HashMap<>();
        Map<String, FixtureScore> scores = new HashMap<>();
        for (MatchDay matchDay : matchDays) {
            Group group = matchDay.getRound() == null ? null : matchDay.getRound().getGroup();
            LeagueRuleSet rules = group == null ? null
                : rulesByGroup.computeIfAbsent(group.getId(), id -> ruleResolver.effectiveFor(group));
            scores.put(matchDay.getId(), score(matchDay, rules, gamesByFixture.getOrDefault(matchDay.getId(), List.of())));
        }
        return scores;
    }

    private static FixtureScore score(MatchDay matchDay, LeagueRuleSet rules, List<Match> games) {
        if (matchDay.isBye()) {
            int winner = rules != null && rules.getRaceByeScoreWinner() != null ? rules.getRaceByeScoreWinner() : 42;
            int loser = rules != null && rules.getRaceByeScoreLoser() != null ? rules.getRaceByeScoreLoser() : 30;
            return new FixtureScore(winner, loser, 0, 0);
        }
        List<Match> entered = games.stream()
            .filter(game -> game.getHomeScore() != null && game.getAwayScore() != null)
            .sorted(Comparator.comparing(Match::getPosition, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
        if (entered.isEmpty()) {
            return new FixtureScore(null, null, 0, games.size());
        }
        if (rules != null && rules.getFixtureMode() == FixtureMode.RACE) {
            Match last = entered.getLast();
            return new FixtureScore(last.getHomeScore(), last.getAwayScore(), entered.size(), games.size());
        }
        int home = 0;
        int away = 0;
        for (Match game : entered) {
            if (game.getHomeScore() > game.getAwayScore()) home++;
            else if (game.getAwayScore() > game.getHomeScore()) away++;
        }
        return new FixtureScore(home, away, entered.size(), games.size());
    }
}
