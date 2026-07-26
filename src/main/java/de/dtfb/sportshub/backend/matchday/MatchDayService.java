package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationNotFoundException;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchNotFoundException;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.match.MatchState;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundNotFoundException;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.context.ApplicationEventPublisher;
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
    private final MatchRepository matchRepository;
    private final ApplicationEventPublisher eventPublisher;

    public MatchDayService(MatchDayRepository repository, MatchDayMapper mapper, RoundRepository roundRepository,
                           LocationRepository locationRepository, TeamRepository teamRepository,
                           MatchRepository matchRepository, ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.roundRepository = roundRepository;
        this.locationRepository = locationRepository;
        this.teamRepository = teamRepository;
        this.matchRepository = matchRepository;
        this.eventPublisher = eventPublisher;
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
        repository.delete(matchDay);
    }

    @Transactional
    public MatchDayDto submitResult(String matchDayId, MatchDayResultRequest request, String submitterDtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));

        if (matchDay.getResultState() != ResultState.OPEN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Result already submitted for this match day");
        }

        for (MatchDayResultRequest.MatchResultEntry entry : request.getMatches()) {
            Match match = matchRepository.findById(entry.getMatchId())
                .orElseThrow(() -> new MatchNotFoundException(entry.getMatchId()));
            if (!match.getMatchDay().getId().equals(matchDayId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match does not belong to this match day");
            }
            match.setHomeScore(entry.getHomeScore());
            match.setAwayScore(entry.getAwayScore());
            match.setState(MatchState.PLAYED);
            matchRepository.save(match);
        }

        matchDay.setResultState(ResultState.HOME_SUBMITTED);
        matchDay.setSubmittedByDtfbId(submitterDtfbId);
        matchDay.setHomeConfirmedAt(Instant.now());
        return mapper.toDto(repository.save(matchDay));
    }

    @Transactional
    public MatchDayDto confirmResult(String matchDayId, String confirmerDtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));

        if (matchDay.getResultState() != ResultState.HOME_SUBMITTED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No submitted result to confirm");
        }
        if (confirmerDtfbId.equals(matchDay.getSubmittedByDtfbId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot confirm your own result submission");
        }

        matchDay.setResultState(ResultState.CONFIRMED);
        matchDay.setAwayConfirmedAt(Instant.now());

        MatchDay saved = repository.save(matchDay);
        eventPublisher.publishEvent(new MatchDayConfirmedEvent(this, saved));
        return mapper.toDto(saved);
    }

    /**
     * A team representative proposes (or counter-proposes) a date/venue for a generated fixture.
     * Either side of the fixture may call this at any point — including to reopen an already
     * {@code CONFIRMED} schedule, mirroring the roster lifecycle's {@code reopen}. See
     * docs/12-matchday-scheduling.md.
     */
    @Transactional
    public MatchDayDto proposeSchedule(String matchDayId, ScheduleProposalRequest request, String proposerDtfbId) {
        MatchDay matchDay = repository.findById(matchDayId)
            .orElseThrow(() -> new MatchDayNotFoundException(matchDayId));

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
