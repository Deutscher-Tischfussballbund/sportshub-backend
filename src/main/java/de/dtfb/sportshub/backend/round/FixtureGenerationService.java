package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.SchedulingMode;
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
import java.util.List;

/**
 * Generates a group's round-robin fixtures: pairs its placed teams into {@link Round}s of
 * {@link MatchDay}s via the standard circle (polygon) method. Deliberately does not create
 * {@link de.dtfb.sportshub.backend.match.Match} rows — the per-game breakdown from a rule set's
 * game plan is a separate, not-yet-built concern. See docs/12-matchday-scheduling.md.
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

    public FixtureGenerationService(GroupRepository groupRepository,
                                     TeamParticipationRepository participationRepository,
                                     RoundRepository roundRepository,
                                     MatchDayRepository matchDayRepository,
                                     RoundMapper roundMapper,
                                     LeagueRuleResolver ruleResolver) {
        this.groupRepository = groupRepository;
        this.participationRepository = participationRepository;
        this.roundRepository = roundRepository;
        this.matchDayRepository = matchDayRepository;
        this.roundMapper = roundMapper;
        this.ruleResolver = ruleResolver;
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
        Instant startDate = request.getStartDate();
        if (startDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required");
        }

        List<List<Team>> singleLegPairings = circleMethodPairings(participations);
        int roundSpacingDays = mode == SchedulingMode.WINDOW ? windowDays : DAY_BATCH_DEFAULT_ROUND_SPACING_DAYS;

        List<Round> rounds = new ArrayList<>();
        int roundIndex = 1;
        for (List<Team> pairing : singleLegPairings) {
            rounds.add(createRound(group, roundIndex, mode, startDate, roundSpacingDays, pairing, false));
            roundIndex++;
        }
        if (request.isDoubleRoundRobin()) {
            for (List<Team> pairing : singleLegPairings) {
                rounds.add(createRound(group, roundIndex, mode, startDate, roundSpacingDays, pairing, true));
                roundIndex++;
            }
        }

        return roundMapper.toDtoList(rounds);
    }

    private Round createRound(Group group, int index, SchedulingMode mode, Instant seasonStart,
                               int roundSpacingDays, List<Team> pairingSlots, boolean swapHomeAway) {
        Round round = new Round();
        round.setGroup(group);
        round.setIndex(index);
        round.setName("Spieltag " + index);

        Instant windowStart = seasonStart.plus((long) (index - 1) * roundSpacingDays, ChronoUnit.DAYS);
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
            matchDay.setStartDate(windowStart);
            matchDay.setResultState(ResultState.OPEN);
            matchDay.setSchedulingState(SchedulingState.DEFAULT);
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
