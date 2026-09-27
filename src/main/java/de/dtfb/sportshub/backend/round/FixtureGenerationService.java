package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.SchedulingMode;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationNotFoundException;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import de.dtfb.sportshub.backend.matchday.ResultState;
import de.dtfb.sportshub.backend.matchday.SchedulingState;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.teamparticipation.ParticipationStatus;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipation;
import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * Generates a group's round-robin fixtures: pairs its placed teams into {@link Round}s of
 * {@link MatchDay}s via the standard circle (polygon) method. Deliberately does not create
 * {@link de.dtfb.sportshub.backend.match.Match} rows — the per-game breakdown from a rule set's
 * game plan is a separate, not-yet-built concern. Optional fixed slots give every round a real
 * kick-off time and venue up front (tournament weekends). A plan can be deleted again while no
 * result has been entered. See docs/12-matchday-scheduling.md.
 */
@Service
public class FixtureGenerationService {

    /** Fallback spacing between rounds in DAY_BATCH mode, where the generated date is only a
     * starting point for the admin's later day-batch assignment — not a real constraint. */
    private static final int DAY_BATCH_DEFAULT_ROUND_SPACING_DAYS = 7;

    private final GroupRepository groupRepository;
    private final TeamParticipationRepository participationRepository;
    private final RoundRepository roundRepository;
    private final MatchDayRepository matchDayRepository;
    private final RoundMapper roundMapper;
    private final LeagueRuleResolver ruleResolver;
    private final LocationRepository locationRepository;
    private final MatchRepository matchRepository;

    public FixtureGenerationService(GroupRepository groupRepository,
                                     TeamParticipationRepository participationRepository,
                                     RoundRepository roundRepository,
                                     MatchDayRepository matchDayRepository,
                                     RoundMapper roundMapper,
                                     LeagueRuleResolver ruleResolver,
                                     LocationRepository locationRepository,
                                     MatchRepository matchRepository) {
        this.groupRepository = groupRepository;
        this.participationRepository = participationRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.roundMapper = roundMapper;
        this.ruleResolver = ruleResolver;
        this.locationRepository = locationRepository;
        this.matchRepository = matchRepository;
    }

    @Transactional
    public List<RoundDto> generate(String groupId, GenerateFixturesRequest request) {
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new GroupNotFoundException(groupId));

        if (roundRepository.existsByGroupId(groupId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Fixtures already generated for this group");
        }

        List<TeamParticipation> participations =
            participationRepository.findByGroup_IdAndStatus(groupId, ParticipationStatus.ACTIVE);
        if (participations.size() < 2) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Need at least two placed teams to generate fixtures");
        }

        LeagueRuleSet ruleSet = ruleResolver.effectiveFor(group);
        SchedulingMode mode = ruleSet != null ? ruleSet.getSchedulingMode() : null;
        if (mode == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "No scheduling mode configured on this group's effective rule set");
        }
        Integer windowDays = ruleSet.getSchedulingWindowDays();
        if (mode == SchedulingMode.WINDOW && (windowDays == null || windowDays < 1)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "WINDOW scheduling mode requires a positive schedulingWindowDays on the rule set");
        }
        List<List<Team>> singleLegPairings = circleMethodPairings(participations);
        List<List<Team>> legs = new ArrayList<>(singleLegPairings);
        if (request.isDoubleRoundRobin()) {
            legs.addAll(singleLegPairings);
        }
        int roundCount = legs.size();

        List<FixtureSlot> slots = request.getSlots() == null ? List.of() : request.getSlots();
        if (!slots.isEmpty()) {
            validateSlots(slots, mode, roundCount);
        }
        Instant startDate = slots.isEmpty() ? request.getStartDate() : slots.getFirst().getStartDate();
        if (startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required");
        }
        Function<String, Location> locationById = locationLookup();

        int roundSpacingDays = mode == SchedulingMode.WINDOW ? windowDays : DAY_BATCH_DEFAULT_ROUND_SPACING_DAYS;
        List<Round> rounds = new ArrayList<>();
        for (int i = 0; i < roundCount; i++) {
            int index = i + 1;
            boolean secondLeg = i >= singleLegPairings.size();
            Instant defaultDate = startDate.plus((long) i * roundSpacingDays, ChronoUnit.DAYS);
            FixtureSlot slot = slots.isEmpty() ? null : slots.get(i);
            rounds.add(createRound(group, index, mode, defaultDate, roundSpacingDays, legs.get(i), secondLeg,
                slot, slot == null ? null : locationById.apply(slot.getLocationId())));
        }

        return roundMapper.toDtoList(rounds);
    }

    /**
     * Deletes a group's whole plan (rounds, fixtures, their not-yet-played games) so it can be
     * generated again, e.g. after a wrong slot. Refused once any fixture has a result entered —
     * from then on the plan is real history, and standings may already count it.
     */
    @Transactional
    public void deleteFixtures(String groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new GroupNotFoundException(groupId);
        }
        List<MatchDay> matchDays = matchDayRepository.findByRoundGroupId(groupId);
        if (matchDays.stream().anyMatch(matchDay -> matchDay.getResultState() != ResultState.OPEN)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Fixtures with entered results cannot be deleted");
        }
        for (MatchDay matchDay : matchDays) {
            matchRepository.deleteAll(matchRepository.findByMatchDay(matchDay));
        }
        matchDayRepository.deleteAll(matchDays);
        roundRepository.deleteAll(roundRepository.findByGroupIdOrderByIndex(groupId));
    }

    private void validateSlots(List<FixtureSlot> slots, SchedulingMode mode, int roundCount) {
        if (mode != SchedulingMode.DAY_BATCH) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Fixed slots require the DAY_BATCH scheduling mode");
        }
        if (slots.size() != roundCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Expected " + roundCount + " slots (one per round), got " + slots.size());
        }
        if (slots.stream().anyMatch(slot -> slot.getStartDate() == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Every slot needs a startDate");
        }
        boolean ascending = IntStream.range(1, slots.size())
            .allMatch(i -> slots.get(i).getStartDate().isAfter(slots.get(i - 1).getStartDate()));
        if (!ascending) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slots must be in ascending order");
        }
    }

    /** Resolves slot venues, loading each distinct one once (a weekend usually shares one venue). */
    private Function<String, Location> locationLookup() {
        Map<String, Location> cache = new HashMap<>();
        return id -> id == null || id.isBlank() ? null : cache.computeIfAbsent(id,
            key -> locationRepository.findById(key).orElseThrow(() -> new LocationNotFoundException(key)));
    }

    private Round createRound(Group group, int index, SchedulingMode mode, Instant windowStart,
                               int roundSpacingDays, List<Team> pairingSlots, boolean swapHomeAway,
                               FixtureSlot slot, Location slotLocation) {
        Round round = new Round();
        round.setGroup(group);
        round.setIndex(index);
        round.setName("Spieltag " + index);

        if (mode == SchedulingMode.WINDOW) {
            round.setWindowStart(windowStart);
            round.setWindowEnd(windowStart.plus(roundSpacingDays, ChronoUnit.DAYS));
        }
        round = roundRepository.save(round);

        for (int i = 0; i < pairingSlots.size(); i += 2) {
            Team teamA = pairingSlots.get(i);
            Team teamB = pairingSlots.get(i + 1);
            if (teamA == null || teamB == null) {
                continue; // bye
            }
            Team home = swapHomeAway ? teamB : teamA;
            Team away = swapHomeAway ? teamA : teamB;

            MatchDay matchDay = new MatchDay();
            matchDay.setRound(round);
            matchDay.setTeamHome(home);
            matchDay.setTeamAway(away);
            matchDay.setResultState(ResultState.OPEN);
            if (slot == null) {
                matchDay.setStartDate(windowStart);
                matchDay.setSchedulingState(SchedulingState.DEFAULT);
            } else {
                // A slot is the organizer's final word, like an admin PUT (docs/12 §3).
                matchDay.setStartDate(slot.getStartDate());
                matchDay.setLocation(slotLocation);
                matchDay.setSchedulingState(SchedulingState.CONFIRMED);
                matchDay.setScheduleConfirmedAt(Instant.now());
            }
            matchDayRepository.save(matchDay);
        }
        return round;
    }

    /**
     * Standard circle (polygon) method: fixes the first team, rotates the rest one position each
     * round. A {@code null} entry pads an odd team count as a bye — whichever team is paired with
     * it sits that round out. Home/away alternates by round parity within each pairing slot — a
     * standard approximation; perfect per-team home/away balance isn't attempted.
     */
    private List<List<Team>> circleMethodPairings(List<TeamParticipation> participations) {
        List<Team> teams = new ArrayList<>();
        for (TeamParticipation participation : participations) {
            teams.add(participation.getTeam());
        }
        if (teams.size() % 2 != 0) {
            teams.add(null); // bye
        }
        int n = teams.size();
        int roundCount = n - 1;

        List<Team> rotation = new ArrayList<>(teams);
        List<List<Team>> pairingsPerRound = new ArrayList<>();

        for (int round = 0; round < roundCount; round++) {
            List<Team> slots = new ArrayList<>();
            for (int i = 0; i < n / 2; i++) {
                Team first = rotation.get(i);
                Team second = rotation.get(n - 1 - i);
                if (round % 2 == 0) {
                    slots.add(first);
                    slots.add(second);
                } else {
                    slots.add(second);
                    slots.add(first);
                }
            }
            pairingsPerRound.add(slots);

            // Rotate: keep index 0 fixed, shift the rest by one — last element moves to index 1.
            Team last = rotation.remove(n - 1);
            rotation.add(1, last);
        }
        return pairingsPerRound;
    }
}
