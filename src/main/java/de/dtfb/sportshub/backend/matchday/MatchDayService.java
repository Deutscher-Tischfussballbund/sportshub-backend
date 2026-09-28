package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.leaguerules.LeagueRuleResolver;
import de.dtfb.sportshub.backend.leaguerules.LeagueRuleSet;
import de.dtfb.sportshub.backend.leaguerules.SchedulingMode;
import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationNotFoundException;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.match.MatchPlanService;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundNotFoundException;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class MatchDayService {
    private final MatchDayRepository repository;
    private final MatchDayMapper mapper;
    private final RoundRepository roundRepository;
    private final LocationRepository locationRepository;
    private final TeamRepository teamRepository;
    private final LeagueRuleResolver ruleResolver;
    private final MatchPlanService matchPlan;

    public MatchDayService(MatchDayRepository repository, MatchDayMapper mapper, RoundRepository roundRepository,
                           LocationRepository locationRepository, TeamRepository teamRepository,
                           LeagueRuleResolver ruleResolver, MatchPlanService matchPlan) {
        this.repository = repository;
        this.mapper = mapper;
        this.roundRepository = roundRepository;
        this.locationRepository = locationRepository;
        this.teamRepository = teamRepository;
        this.ruleResolver = ruleResolver;
        this.matchPlan = matchPlan;
    }

    @Transactional(readOnly = true)
    public List<MatchDayDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public MatchDayDto get(String id) {
        MatchDay matchDay = repository.findVisibleById(id).orElseThrow(
            () -> new MatchDayNotFoundException(id));
        return mapper.toDto(matchDay);
    }

    @Transactional
    public MatchDayDto create(MatchDayDto matchDayDto) {
        MatchDay matchDay = mapper.toEntity(matchDayDto);

        setDependants(matchDayDto, matchDay);

        MatchDay savedMatchDay = repository.save(matchDay);
        matchPlan.createGames(savedMatchDay);
        return mapper.toDto(savedMatchDay);
    }

    @Transactional
    public MatchDayDto update(String id, MatchDayDto matchDayDto) {
        MatchDay matchDay = repository.findById(id).orElseThrow(
            () -> new MatchDayNotFoundException(id));

        mapper.updateEntityFromDto(matchDayDto, matchDay);

        setDependants(matchDayDto, matchDay);

        // A full admin PUT is authoritative over the fixture's date/venue — same admin-bypass
        // precedent as the roster edit bypass (backend PR #30) — and finalizes any pending
        // schedule negotiation between the two teams.
        matchDay.setSchedulingState(SchedulingState.CONFIRMED);
        matchDay.setScheduleProposedByDtfbId(null);
        matchDay.setScheduleConfirmedAt(Instant.now());

        MatchDay savedMatchDay = repository.save(matchDay);
        return mapper.toDto(savedMatchDay);
    }

    @Transactional
    public void delete(String id) {
        MatchDay matchDay = repository.findById(id).orElseThrow(
            () -> new MatchDayNotFoundException(id));
        matchPlan.deleteGames(matchDay);
        repository.delete(matchDay);
    }

    /**
     * A team representative proposes (or counter-proposes) a date/venue for a generated fixture.
     * Either side of the fixture may call this at any point — including to reopen an already
     * {@code CONFIRMED} schedule, mirroring the roster lifecycle's {@code reopen}. Only in a
     * {@code WINDOW} league — with fixed matchdays the organizer sets the dates (409). See
     * docs/12-matchday-scheduling.md.
     */
    @Transactional
    public MatchDayDto proposeSchedule(String matchDayId, ScheduleProposalRequest request, String proposerDtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));

        requireNegotiableDates(matchDay);
        if (request.getStartDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate is required");
        }
        Round round = matchDay.getRound();
        if (round != null && round.getWindowStart() != null && round.getWindowEnd() != null
                && (request.getStartDate().isBefore(round.getWindowStart())
                    || request.getStartDate().isAfter(round.getWindowEnd()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Proposed date is outside the round's scheduling window");
        }

        matchDay.setStartDate(request.getStartDate());
        if (request.getLocationId() != null) {
            Location location = locationRepository.findById(request.getLocationId())
                .orElseThrow(() -> new LocationNotFoundException(request.getLocationId()));
            matchDay.setLocation(location);
        }
        matchDay.setSchedulingState(SchedulingState.PROPOSED);
        matchDay.setScheduleProposedByDtfbId(proposerDtfbId);
        matchDay.setScheduleConfirmedAt(null);

        return mapper.toDto(repository.save(matchDay));
    }

    /**
     * The *other* team representative accepts a pending proposal — the submitter-vs-opponent
     * invariant already proven for match results (a person may not accept their own proposal).
     */
    @Transactional
    public MatchDayDto acceptSchedule(String matchDayId, String accepterDtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));

        requireNegotiableDates(matchDay);
        if (matchDay.getSchedulingState() != SchedulingState.PROPOSED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No pending schedule proposal to accept");
        }
        if (accepterDtfbId.equals(matchDay.getScheduleProposedByDtfbId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot accept your own schedule proposal");
        }

        matchDay.setSchedulingState(SchedulingState.CONFIRMED);
        matchDay.setScheduleConfirmedAt(Instant.now());
        return mapper.toDto(repository.save(matchDay));
    }

    /** Teams agree on dates only where the group's effective rules say so (WINDOW, docs/12 §1);
     * otherwise — fixed matchdays, fixed slots, or no mode — the organizer sets them. */
    private void requireNegotiableDates(MatchDay matchDay) {
        Round round = matchDay.getRound();
        LeagueRuleSet rules = round == null || round.getGroup() == null ? null : ruleResolver.effectiveFor(round.getGroup());
        if (rules == null || rules.getSchedulingMode() != SchedulingMode.WINDOW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Dates in this league are set by the organizer; teams can only agree on dates in a scheduling window");
        }
    }

    private void setDependants(MatchDayDto matchDayDto, MatchDay matchDay) {
        Round round = roundRepository.findById(matchDayDto.getRoundId())
            .orElseThrow(() -> new RoundNotFoundException(matchDayDto.getRoundId()));
        matchDay.setRound(round);
        // A generated-but-not-yet-agreed fixture may have no venue yet (see docs/12-matchday-scheduling.md).
        if (matchDayDto.getLocationId() != null) {
            Location location = locationRepository.findById(matchDayDto.getLocationId())
                .orElseThrow(() -> new LocationNotFoundException(matchDayDto.getLocationId()));
            matchDay.setLocation(location);
        } else {
            matchDay.setLocation(null);
        }
        Team teamAway = teamRepository.findById(matchDayDto.getTeamAwayId())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamAwayId()));
        matchDay.setTeamAway(teamAway);
        Team teamHome = teamRepository.findById(matchDayDto.getTeamHomeId())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamHomeId()));
        matchDay.setTeamHome(teamHome);
    }
}
