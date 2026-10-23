package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupKind;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.knockout.FinalRankingRowDto.Basis;
import de.dtfb.sportshub.backend.knockout.FinalRankingRowDto.Mark;
import de.dtfb.sportshub.backend.matchday.FixtureScoreService;
import de.dtfb.sportshub.backend.matchday.FixtureSource;
import de.dtfb.sportshub.backend.matchday.FixtureSourceType;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.standing.StandingDto;
import de.dtfb.sportshub.backend.standing.StandingService;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierNotFoundException;
import de.dtfb.sportshub.backend.tier.TierRepository;
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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * A tier's final ranking (docs/34). Knock-out fixtures linked by winner/loser sources form a <b>block</b>
 * (a main bracket, a placement block); a block with E entering group places covers the places
 * {@code p0 … p0+E−1}, where {@code p0 = (first group place − 1) × groups + 1}. Inside a block a fixture's
 * {@code winnerPlace}/{@code loserPlace} decide exact places; a team that drops out without one shares
 * the block's remaining places with the others of its round -- a later round ranks better (no game for
 * 3rd: the semi-final losers share 3–4, the quarter-final losers 5–8). A team in no block is ranked by its
 * group place, shared across the groups ({@code (place − 1) × groups + 1 …}). Promotion / relegation marks
 * only cover a range that lies wholly inside the tier's places.
 */
@Service
public class FinalRankingService {

    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final MatchDayRepository matchDayRepository;
    private final StandingService standingService;
    private final FixtureScoreService scoreService;

    public FinalRankingService(TierRepository tierRepository, GroupRepository groupRepository,
                               MatchDayRepository matchDayRepository, StandingService standingService,
                               FixtureScoreService scoreService) {
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.matchDayRepository = matchDayRepository;
        this.standingService = standingService;
        this.scoreService = scoreService;
    }

    @Transactional(readOnly = true)
    public List<FinalRankingRowDto> get(String tierId) {
        Tier tier = tierRepository.findVisibleById(tierId).orElseThrow(() -> new TierNotFoundException(tierId));
        return rank(tier);
    }

    @Transactional(readOnly = true)
    public List<FinalRankingRowDto> rank(Tier tier) {
        List<Group> roundRobins = new ArrayList<>();
        List<MatchDay> knockoutFixtures = new ArrayList<>();
        for (Group group : groupRepository.findByTierId(tier.getId())) {
            if (group.getKind() == GroupKind.KNOCKOUT) {
                knockoutFixtures.addAll(matchDayRepository.findByRoundGroupId(group.getId()));
            } else {
                roundRobins.add(group);
            }
        }
        roundRobins.sort(Comparator.comparing(Group::getName, Comparator.nullsLast(Comparator.naturalOrder())));
        int groupCount = Math.max(1, roundRobins.size());

        Map<String, FinalRankingRowDto> rows = new LinkedHashMap<>();
        Set<String> leafSlots = new HashSet<>(); // "groupId#place" taken into a knock-out block
        for (Block block : blocks(knockoutFixtures)) {
            block.leaves.forEach(leaf -> leafSlots.add(slot(leaf.getGroup().getId(), leaf.getPlace())));
            rankBlock(block, groupCount, rows);
        }

        for (Group group : roundRobins) {
            boolean finished = isFinished(group);
            for (StandingDto standing : standingService.getByGroup(group.getId())) {
                if (rows.containsKey(standing.getTeamId())) continue;
                FinalRankingRowDto row = row(standing.getTeamId(), standing.getTeamName());
                if (standing.isWithdrawn()) {
                    row.setWithdrawn(true);
                } else if (standing.getPlace() != null && !leafSlots.contains(slot(group.getId(), standing.getPlace()))) {
                    row.setPlaceFrom((standing.getPlace() - 1) * groupCount + 1);
                    row.setPlaceTo(standing.getPlace() * groupCount);
                    row.setBasis(Basis.GROUP_PLACE);
                    row.setDetail(group.getName());
                    row.setProvisional(!finished);
                } else {
                    row.setProvisional(true); // on its way into a knock-out stage
                }
                rows.put(row.getTeamId(), row);
            }
        }

        List<FinalRankingRowDto> ranking = new ArrayList<>(rows.values());
        mark(tier, ranking);
        ranking.sort(Comparator.comparing(FinalRankingRowDto::isWithdrawn)
            .thenComparing(FinalRankingRowDto::getPlaceFrom, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FinalRankingRowDto::getTeamName, Comparator.nullsLast(Comparator.naturalOrder())));
        return ranking;
    }

    /** Fixtures connected through winner/loser sources, with their group-place leaves. */
    private static final class Block {
        final List<MatchDay> fixtures = new ArrayList<>();
        final List<FixtureSource> leaves = new ArrayList<>();
    }

    private List<Block> blocks(List<MatchDay> fixtures) {
        Map<String, String> parent = new HashMap<>();
        fixtures.forEach(f -> parent.put(f.getId(), f.getId()));
        for (MatchDay fixture : fixtures) {
            for (FixtureSource source : sources(fixture)) {
                if (source.getMatchDay() != null && parent.containsKey(source.getMatchDay().getId())) {
                    parent.put(find(parent, fixture.getId()), find(parent, source.getMatchDay().getId()));
                }
            }
        }
        Map<String, Block> byRoot = new LinkedHashMap<>();
        for (MatchDay fixture : fixtures) {
            Block block = byRoot.computeIfAbsent(find(parent, fixture.getId()), root -> new Block());
            block.fixtures.add(fixture);
            for (FixtureSource source : sources(fixture)) {
                if (source.getType() == FixtureSourceType.GROUP_PLACE && source.getGroup() != null && source.getPlace() != null) {
                    block.leaves.add(source);
                }
            }
        }
        return new ArrayList<>(byRoot.values());
    }

    private void rankBlock(Block block, int groupCount, Map<String, FinalRankingRowDto> rows) {
        if (block.leaves.isEmpty()) return;
        int firstPlace = block.leaves.stream().mapToInt(FixtureSource::getPlace).min().orElse(1);
        int p0 = (firstPlace - 1) * groupCount + 1;
        TreeSet<Integer> open = new TreeSet<>();
        for (int place = p0; place < p0 + block.leaves.size(); place++) open.add(place);

        Set<String> fedLosers = new HashSet<>();
        for (MatchDay fixture : block.fixtures) {
            for (FixtureSource source : sources(fixture)) {
                if (source.getType() == FixtureSourceType.LOSER && source.getMatchDay() != null) {
                    fedLosers.add(source.getMatchDay().getId());
                }
            }
            if (fixture.getWinnerPlace() != null) open.remove(fixture.getWinnerPlace());
            if (fixture.getLoserPlace() != null) open.remove(fixture.getLoserPlace());
        }
        Map<String, FixtureScoreService.FixtureScore> scores = scoreService.scores(block.fixtures);

        // Rounds whose losers drop out without a place: round index → (number of such fixtures, losers so far).
        TreeMap<Integer, List<MatchDay>> dropOutFixtures = new TreeMap<>(Comparator.reverseOrder());
        for (MatchDay fixture : block.fixtures) {
            Team home = fixture.getTeamHome();
            Team away = fixture.getTeamAway();
            for (Team team : new Team[] {home, away}) {
                if (team != null) rows.computeIfAbsent(team.getId(), id -> pending(team));
            }
            boolean dropOut = fixture.getLoserPlace() == null && !fedLosers.contains(fixture.getId());
            if (dropOut) {
                dropOutFixtures.computeIfAbsent(fixture.getRound().getIndex(), r -> new ArrayList<>()).add(fixture);
            }
            Team[] outcome = outcome(fixture, scores.get(fixture.getId()));
            if (outcome == null) continue;
            if (fixture.getWinnerPlace() != null) place(rows, outcome[0], fixture.getWinnerPlace(), fixture.getWinnerPlace(), Basis.KNOCKOUT_GAME, fixture.getName());
            if (fixture.getLoserPlace() != null) place(rows, outcome[1], fixture.getLoserPlace(), fixture.getLoserPlace(), Basis.KNOCKOUT_GAME, fixture.getName());
        }
        // Later rounds first: they take the better of the places left.
        for (List<MatchDay> round : dropOutFixtures.values()) {
            int from = open.isEmpty() ? 0 : open.first();
            int to = from;
            for (int i = 0; i < round.size() && !open.isEmpty(); i++) {
                to = open.pollFirst();
            }
            for (MatchDay fixture : round) {
                Team[] outcome = outcome(fixture, scores.get(fixture.getId()));
                if (outcome != null && from > 0) {
                    place(rows, outcome[1], from, to, Basis.KNOCKOUT_ROUND, fixture.getRound().getName());
                }
            }
        }
    }

    /** {winner, loser} of a final fixture with two teams, or null while it's open or level. */
    private static Team[] outcome(MatchDay fixture, FixtureScoreService.FixtureScore score) {
        if (fixture.getResultState() != ResultState.CONFIRMED || fixture.getTeamHome() == null || fixture.getTeamAway() == null
            || score == null || score.home() == null || score.away() == null || score.home().equals(score.away())) {
            return null;
        }
        return score.home() > score.away()
            ? new Team[] {fixture.getTeamHome(), fixture.getTeamAway()}
            : new Team[] {fixture.getTeamAway(), fixture.getTeamHome()};
    }

    private static void place(Map<String, FinalRankingRowDto> rows, Team team, int from, int to, Basis basis, String detail) {
        FinalRankingRowDto row = rows.computeIfAbsent(team.getId(), id -> pending(team));
        row.setPlaceFrom(from);
        row.setPlaceTo(to);
        row.setBasis(basis);
        row.setDetail(detail);
        row.setProvisional(false);
    }

    private static FinalRankingRowDto pending(Team team) {
        FinalRankingRowDto row = row(team.getId(), team.getName());
        row.setProvisional(true);
        return row;
    }

    private static FinalRankingRowDto row(String teamId, String teamName) {
        FinalRankingRowDto row = new FinalRankingRowDto();
        row.setTeamId(teamId);
        row.setTeamName(teamName);
        return row;
    }

    /** Promotion: the whole range within the top places; relegation: within the bottom places. */
    private static void mark(Tier tier, List<FinalRankingRowDto> ranking) {
        long ranked = ranking.stream().filter(row -> !row.isWithdrawn()).count();
        for (FinalRankingRowDto row : ranking) {
            if (row.isWithdrawn() || row.getPlaceFrom() == null) continue;
            if (tier.getPromotionPlaces() != null && row.getPlaceTo() <= tier.getPromotionPlaces()) {
                row.setMark(Mark.PROMOTED);
            } else if (tier.getRelegationPlaces() != null && row.getPlaceFrom() > ranked - tier.getRelegationPlaces()) {
                row.setMark(Mark.RELEGATED);
            }
        }
    }

    private boolean isFinished(Group group) {
        return !matchDayRepository.findByRoundGroupId(group.getId()).isEmpty()
            && !matchDayRepository.existsByRound_Group_IdAndResultStateNotAndByeFalse(group.getId(), ResultState.CONFIRMED);
    }

    private static List<FixtureSource> sources(MatchDay fixture) {
        List<FixtureSource> sources = new ArrayList<>(2);
        if (fixture.getHomeSource() != null) sources.add(fixture.getHomeSource());
        if (fixture.getAwaySource() != null) sources.add(fixture.getAwaySource());
        return sources;
    }

    private static String find(Map<String, String> parent, String id) {
        String root = id;
        while (!Objects.equals(parent.get(root), root)) root = parent.get(root);
        return root;
    }

    private static String slot(String groupId, int place) {
        return groupId + "#" + place;
    }
}
