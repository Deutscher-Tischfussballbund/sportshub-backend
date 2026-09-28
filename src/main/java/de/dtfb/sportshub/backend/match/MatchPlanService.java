package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.league.LeagueRepository;
import de.dtfb.sportshub.backend.leaguerules.GamePlanEntry;
import de.dtfb.sportshub.backend.leaguerules.GamePlanEntryRepository;
import de.dtfb.sportshub.backend.leaguerules.GamePlanLockedException;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A fixture's individual games (SPO-71): every {@link MatchDay} of a group gets one {@link Match}
 * per entry of the group's effective game plan (docs/09 §3.1), in plan order. The game plan is
 * fixed from the first entered result on: until then a change to it rebuilds the games of every
 * fixture it governs (dates, venues and slots stay untouched); afterwards it is refused
 * ({@code 409 GAME_PLAN_LOCKED}). See docs/12-matchday-scheduling.md §5.
 */
@Service
public class MatchPlanService {

    private final MatchRepository matchRepository;
    private final MatchDayRepository matchDayRepository;
    private final GamePlanEntryRepository gamePlanRepository;
    private final LeagueRuleResolver ruleResolver;
    private final GroupRepository groupRepository;
    private final LeagueRepository leagueRepository;
    private final TierRepository tierRepository;

    public MatchPlanService(MatchRepository matchRepository,
                            MatchDayRepository matchDayRepository,
                            GamePlanEntryRepository gamePlanRepository,
                            LeagueRuleResolver ruleResolver,
                            GroupRepository groupRepository,
                            LeagueRepository leagueRepository,
                            TierRepository tierRepository) {
        this.matchRepository = matchRepository;
        this.matchDayRepository = matchDayRepository;
        this.gamePlanRepository = gamePlanRepository;
        this.ruleResolver = ruleResolver;
        this.groupRepository = groupRepository;
        this.leagueRepository = leagueRepository;
        this.tierRepository = tierRepository;
    }

    /** Creates a new fixture's games from its group's effective game plan. No group or no plan: no games. */
    @Transactional
    public void createGames(MatchDay matchDay) {
        Group group = matchDay.getRound() == null ? null : matchDay.getRound().getGroup();
        for (GamePlanEntry entry : gamePlanOf(group)) {
            Match match = new Match();
            match.setMatchDay(matchDay);
            match.setPosition(entry.getPosition());
            match.setType(entry.getGameType());
            match.setState(MatchState.PLANNED);
            match.setStartTime(matchDay.getStartDate());
            matchRepository.save(match);
        }
    }

    /** Deletes a fixture's games, e.g. before the fixture itself is deleted. */
    @Transactional
    public void deleteGames(MatchDay matchDay) {
        matchRepository.deleteAll(matchRepository.findByMatchDay(matchDay));
    }

    /** The groups of a league (all its tiers) -- what a change of the league's rules can affect. */
    @Transactional(readOnly = true)
    public List<Group> groupsOfLeague(String leagueId) {
        return groupRepository.findByTier_League_Id(leagueId);
    }

    /** The groups of one tier -- what a change of the tier's rules override can affect. */
    @Transactional(readOnly = true)
    public List<Group> groupsOfTier(String tierId) {
        return groupRepository.findByTierId(tierId);
    }

    /** The groups whose effective rules are this snapshot. Empty for a blueprint. */
    @Transactional(readOnly = true)
    public List<Group> groupsGovernedBy(LeagueRuleSet ruleSet) {
        if (ruleSet == null || !ruleSet.isSnapshot()) {
            return List.of();
        }
        List<Group> candidates = leagueRepository.findFirstByRuleSetId(ruleSet.getId())
            .map(league -> groupsOfLeague(league.getId()))
            .orElseGet(() -> tierRepository.findFirstByRuleSetId(ruleSet.getId())
                .map(tier -> groupsOfTier(tier.getId()))
                .orElse(List.of()));
        return candidates.stream()
            .filter(group -> {
                LeagueRuleSet effective = ruleResolver.effectiveFor(group);
                return effective != null && ruleSet.getId().equals(effective.getId());
            })
            .toList();
    }

    /** Whether a result has been entered in a group this snapshot governs -- its game plan is then fixed. */
    @Transactional(readOnly = true)
    public boolean isGamePlanLocked(LeagueRuleSet ruleSet) {
        return groupsGovernedBy(ruleSet).stream().anyMatch(this::hasResult);
    }

    /** The groups' effective game plans, taken before a rule change -- hand them to {@link #afterRuleChange}. */
    @Transactional(readOnly = true)
    public Map<String, List<String>> planSignatures(Collection<Group> groups) {
        Map<String, List<String>> signatures = new HashMap<>();
        for (Group group : groups) {
            signatures.put(group.getId(), signature(gamePlanOf(group)));
        }
        return signatures;
    }

    /**
     * After a rule change: every group whose effective game plan now differs from {@code before}
     * gets its fixtures' games rebuilt. Refused with {@link GamePlanLockedException} -- rolling back
     * the whole change -- if a result has already been entered in such a group.
     */
    @Transactional
    public void afterRuleChange(Collection<Group> groups, Map<String, List<String>> before) {
        for (Group group : groups) {
            if (Objects.equals(before.get(group.getId()), signature(gamePlanOf(group)))) {
                continue;
            }
            if (hasResult(group)) {
                throw new GamePlanLockedException(
                    "A result has been entered in this league, so its game plan can no longer be changed");
            }
            for (MatchDay matchDay : matchDayRepository.findByRoundGroupId(group.getId())) {
                deleteGames(matchDay);
                createGames(matchDay);
            }
        }
    }

    /**
     * Gives fixtures from before SPO-71 their games: every fixture of a group that has no games and
     * no result yet. Idempotent. Returns the number of fixtures filled.
     */
    @Transactional
    public int backfill() {
        int filled = 0;
        for (MatchDay matchDay : matchDayRepository.findWithoutGames(ResultState.OPEN)) {
            createGames(matchDay);
            filled++;
        }
        return filled;
    }

    private boolean hasResult(Group group) {
        return matchDayRepository.existsByRound_Group_IdAndResultStateNot(group.getId(), ResultState.OPEN)
            || matchRepository.existsScoredInGroup(group.getId());
    }

    private List<GamePlanEntry> gamePlanOf(Group group) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(group);
        return rules == null ? List.of() : gamePlanRepository.findByRuleSetIdOrderByPositionAsc(rules.getId());
    }

    private static List<String> signature(List<GamePlanEntry> plan) {
        return plan.stream().map(entry -> entry.getPosition() + ":" + entry.getGameType()).toList();
    }
}
