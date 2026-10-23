package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupKind;
import de.dtfb.sportshub.backend.group.GroupMapper;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.group.GroupState;
import de.dtfb.sportshub.backend.leaguerules.FixtureMode;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.MatchdayDecision;
import de.dtfb.sportshub.backend.leaguerules.RaceEndRule;
import de.dtfb.sportshub.backend.leaguerules.RuleSetSnapshotService;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationNotFoundException;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.match.MatchPlanService;
import de.dtfb.sportshub.backend.matchday.FixtureSource;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.round.FixtureSlot;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierNotFoundException;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The knock-out helper (docs/34): creates a knock-out group in a tier with source-based fixtures in the
 * usual shapes -- a main bracket fed by group places (standard seeding; with two groups crossed: 1A–2B,
 * 1B–2A; 1A–4B, 2B–3A, 1B–4A, 2A–3B), an optional game for 3rd, and placement games for the remaining
 * places of two groups. Rounds count from the end: the last round holds the final, the game for 3rd and
 * every placement final, so the time slots line up as on a tournament weekend. A shortcut, not a format:
 * all of it stays editable through {@link KnockoutService#update}.
 */
@Service
public class KnockoutStageService {

    static final String DRAW_POSSIBLE = "DRAW_POSSIBLE";

    private final TierRepository tierRepository;
    private final GroupRepository groupRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final TeamParticipationRepository participationRepository;
    private final LocationRepository locationRepository;
    private final RuleSetSnapshotService snapshots;
    private final LeagueRuleResolver ruleResolver;
    private final MatchPlanService matchPlan;
    private final KnockoutService knockout;
    private final GroupMapper groupMapper;

    public KnockoutStageService(TierRepository tierRepository, GroupRepository groupRepository,
                                RoundRepository roundRepository, MatchDayRepository matchDayRepository,
                                TeamParticipationRepository participationRepository,
                                LocationRepository locationRepository, RuleSetSnapshotService snapshots,
                                LeagueRuleResolver ruleResolver, MatchPlanService matchPlan,
                                KnockoutService knockout, GroupMapper groupMapper) {
        this.tierRepository = tierRepository;
        this.groupRepository = groupRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.participationRepository = participationRepository;
        this.locationRepository = locationRepository;
        this.snapshots = snapshots;
        this.ruleResolver = ruleResolver;
        this.matchPlan = matchPlan;
        this.knockout = knockout;
        this.groupMapper = groupMapper;
    }

    @Transactional
    public KnockoutStageDto create(String tierId, KnockoutStageRequest request) {
        Tier tier = tierRepository.findById(tierId).orElseThrow(() -> new TierNotFoundException(tierId));
        snapshots.requireSeasonRunning(tier.getLeague().getSeason());
        List<Group> sources = sourceGroups(tier, request.getSourceGroupIds());
        int groups = sources.size();
        int perGroup = request.getQualifiersPerGroup();
        int fromPlace = request.getFromPlace() == null ? 1 : request.getFromPlace();
        int size = groups * perGroup;
        if (perGroup < 1 || fromPlace < 1 || !List.of(2, 4, 8, 16).contains(size)) {
            throw badRequest("Groups × qualifiers per group must be 2, 4, 8 or 16 (a full bracket)");
        }
        int smallest = sources.stream().mapToInt(this::placedTeams).min().orElse(0);
        if (smallest < fromPlace + perGroup - 1) {
            throw badRequest("A source group has fewer placed teams than the bracket takes from it");
        }
        if (request.isPlacementGames() && groups != 2) {
            throw badRequest("Placement games pair the places of two groups; choose two source groups");
        }
        int rounds = Integer.numberOfTrailingZeros(size); // log2
        if (request.isPlacementGames() && rounds < 2 && smallest - (fromPlace + perGroup - 1) >= 2) {
            throw badRequest("Placement games for four teams need a bracket of at least two rounds");
        }
        List<FixtureSlot> slots = request.getSlots() == null ? List.of() : request.getSlots();
        if (!slots.isEmpty() && slots.size() != rounds) {
            throw badRequest("Give one slot per round (" + rounds + ")");
        }
        if (slots.stream().anyMatch(slot -> slot == null || slot.getStartDate() == null)) {
            throw badRequest("Every slot needs a kick-off");
        }
        if (slots.isEmpty() && request.getStartDate() == null) {
            throw badRequest("Give the slots or a start date");
        }

        Group stage = new Group();
        stage.setTier(tier);
        stage.setName(request.getName() == null || request.getName().isBlank() ? "K.O.-Runde" : request.getName().trim());
        stage.setKind(GroupKind.KNOCKOUT);
        stage.setGroupState(GroupState.PLANNED);
        if (request.getBlueprintId() != null) {
            stage.setRuleSet(snapshots.snapshotOf(snapshots.requireBlueprint(request.getBlueprintId()),
                tier.getLeague().getSeason().getFederation()));
        }
        stage = groupRepository.save(stage);

        List<Instant> kickOffs = new ArrayList<>();
        List<Location> venues = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            FixtureSlot slot = slots.isEmpty() ? null : slots.get(i);
            kickOffs.add(slot == null ? request.getStartDate() : slot.getStartDate());
            venues.add(slot == null ? null : location(slot.getLocationId()));
        }
        Builder builder = new Builder(createRounds(stage, rounds), kickOffs, venues, !slots.isEmpty());
        int firstRankingPlace = (fromPlace - 1) * groups + 1;
        builder.mainBracket(sources, fromPlace, perGroup, firstRankingPlace, request.isThirdPlaceGame());
        if (request.isPlacementGames()) {
            int place = fromPlace + perGroup;
            int ranking = firstRankingPlace + size;
            while (smallest - place + 1 >= 2) {
                builder.placementOfFour(sources.get(0), sources.get(1), place, ranking);
                place += 2;
                ranking += 4;
            }
            if (smallest - place + 1 == 1) {
                builder.placementOfTwo(sources.get(0), sources.get(1), place, ranking);
            }
        }

        knockout.refreshTier(tier); // source groups already finished: fill at once
        return new KnockoutStageDto(groupMapper.toDto(stage), warnings(stage));
    }

    /** Round-robin groups of this tier, one or two, distinct. */
    private List<Group> sourceGroups(Tier tier, List<String> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 2 || new HashSet<>(ids).size() != ids.size()) {
            throw badRequest("Choose one or two different source groups");
        }
        List<Group> groups = new ArrayList<>();
        for (String id : ids) {
            Group group = groupRepository.findById(id).orElseThrow(() -> new GroupNotFoundException(id));
            if (!Objects.equals(group.getTier().getId(), tier.getId()) || group.getKind() != GroupKind.ROUND_ROBIN) {
                throw badRequest("A source group must be a round robin of the same tier");
            }
            groups.add(group);
        }
        return groups;
    }

    private int placedTeams(Group group) {
        return participationRepository.findByGroup_IdAndStatus(group.getId(), ParticipationStatus.ACTIVE).size();
    }

    private List<Round> createRounds(Group stage, int count) {
        List<Round> rounds = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Round round = new Round();
            round.setGroup(stage);
            round.setIndex(i);
            round.setName(roundName(1 << (count - i + 1)));
            rounds.add(roundRepository.save(round));
        }
        return rounds;
    }

    private Location location(String id) {
        return id == null || id.isBlank() ? null
            : locationRepository.findById(id).orElseThrow(() -> new LocationNotFoundException(id));
    }

    private static String roundName(int teams) {
        return switch (teams) {
            case 2 -> "Finale";
            case 4 -> "Halbfinale";
            case 8 -> "Viertelfinale";
            default -> "Achtelfinale";
        };
    }

    private static String fixturePrefix(int teams) {
        return switch (teams) {
            case 4 -> "HF";
            case 8 -> "VF";
            default -> "AF";
        };
    }

    /** {@link #DRAW_POSSIBLE} when the knock-out group's rules let a fixture end level. */
    private List<String> warnings(Group stage) {
        LeagueRuleSet rules = ruleResolver.effectiveFor(stage);
        boolean drawPossible = rules == null
            || (rules.getFixtureMode() == FixtureMode.RACE && rules.getRaceEndRule() != RaceEndRule.TWO_POINT_LEAD)
            || (rules.getFixtureMode() != FixtureMode.RACE && rules.getMatchdayDecision() != MatchdayDecision.FIRST_TO);
        return drawPossible ? List.of(DRAW_POSSIBLE) : List.of();
    }

    /** Standard bracket seeding order: 1–2; 1–4, 2–3; 1–8, 4–5, 2–7, 3–6 ... (top seeds meet last). */
    static List<Integer> seedOrder(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int next = order.size() * 2 + 1;
            List<Integer> expanded = new ArrayList<>();
            for (int seed : order) {
                expanded.add(seed);
                expanded.add(next - seed);
            }
            order = expanded;
        }
        return order;
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    /** Creates the fixtures of one knock-out group, round by round. */
    private final class Builder {
        private final List<Round> rounds;
        private final List<Instant> kickOffs;
        private final List<Location> venues;
        private final boolean fromSlots;

        Builder(List<Round> rounds, List<Instant> kickOffs, List<Location> venues, boolean fromSlots) {
            this.rounds = rounds;
            this.kickOffs = kickOffs;
            this.venues = venues;
            this.fromSlots = fromSlots;
        }

        /** Seeds interleave the groups (1A, 1B, 2A, 2B, ...), so standard seeding pairs them crossed. */
        void mainBracket(List<Group> sources, int fromPlace, int perGroup, int firstPlace, boolean thirdPlaceGame) {
            int groups = sources.size();
            int size = groups * perGroup;
            List<FixtureSource> current = new ArrayList<>();
            for (int seed : seedOrder(size)) {
                current.add(FixtureSource.groupPlace(sources.get((seed - 1) % groups), fromPlace + (seed - 1) / groups));
            }
            int round = 0;
            List<MatchDay> lastRound = List.of();
            while (current.size() >= 2) {
                int teams = current.size();
                List<MatchDay> fixtures = new ArrayList<>();
                for (int i = 0; i < teams; i += 2) {
                    String name = teams == 2 ? "Finale" : fixturePrefix(teams) + " " + (i / 2 + 1);
                    MatchDay fixture = fixture(round, name, current.get(i), current.get(i + 1));
                    if (teams == 2) {
                        fixture.setWinnerPlace(firstPlace);
                        fixture.setLoserPlace(firstPlace + 1);
                    }
                    fixtures.add(save(fixture));
                }
                if (teams == 2 && thirdPlaceGame && !lastRound.isEmpty()) {
                    MatchDay third = fixture(round, "Spiel um Platz " + (firstPlace + 2),
                        FixtureSource.loserOf(lastRound.get(0)), FixtureSource.loserOf(lastRound.get(1)));
                    third.setWinnerPlace(firstPlace + 2);
                    third.setLoserPlace(firstPlace + 3);
                    save(third);
                }
                current = fixtures.stream().map(FixtureSource::winnerOf).toList();
                lastRound = fixtures;
                round++;
            }
        }

        /** Places p, p+1 of both groups → two semi-finals (crossed), then the games for {@code ranking} and +2. */
        void placementOfFour(Group a, Group b, int place, int ranking) {
            int semiRound = rounds.size() - 2;
            String block = ranking + "–" + (ranking + 3);
            MatchDay semi1 = save(fixture(semiRound, "Platz " + block + " HF 1",
                FixtureSource.groupPlace(a, place), FixtureSource.groupPlace(b, place + 1)));
            MatchDay semi2 = save(fixture(semiRound, "Platz " + block + " HF 2",
                FixtureSource.groupPlace(b, place), FixtureSource.groupPlace(a, place + 1)));
            MatchDay upper = fixture(rounds.size() - 1, "Spiel um Platz " + ranking,
                FixtureSource.winnerOf(semi1), FixtureSource.winnerOf(semi2));
            upper.setWinnerPlace(ranking);
            upper.setLoserPlace(ranking + 1);
            save(upper);
            MatchDay lower = fixture(rounds.size() - 1, "Spiel um Platz " + (ranking + 2),
                FixtureSource.loserOf(semi1), FixtureSource.loserOf(semi2));
            lower.setWinnerPlace(ranking + 2);
            lower.setLoserPlace(ranking + 3);
            save(lower);
        }

        /** Place p of both groups → one game for {@code ranking}. */
        void placementOfTwo(Group a, Group b, int place, int ranking) {
            MatchDay game = fixture(rounds.size() - 1, "Spiel um Platz " + ranking,
                FixtureSource.groupPlace(a, place), FixtureSource.groupPlace(b, place));
            game.setWinnerPlace(ranking);
            game.setLoserPlace(ranking + 1);
            save(game);
        }

        private MatchDay fixture(int roundIndex, String name, FixtureSource home, FixtureSource away) {
            MatchDay fixture = new MatchDay();
            fixture.setRound(rounds.get(roundIndex));
            fixture.setName(name);
            fixture.setHomeSource(home);
            fixture.setAwaySource(away);
            fixture.setResultState(ResultState.OPEN);
            fixture.setStartDate(kickOffs.get(roundIndex));
            fixture.setLocation(venues.get(roundIndex));
            if (fromSlots) {
                fixture.setSchedulingState(SchedulingState.CONFIRMED); // the organizer's slot (docs/12 §3)
                fixture.setScheduleConfirmedAt(Instant.now());
            }
            return fixture;
        }

        private MatchDay save(MatchDay fixture) {
            MatchDay saved = matchDayRepository.save(fixture);
            matchPlan.createGames(saved);
            return saved;
        }
    }
}
