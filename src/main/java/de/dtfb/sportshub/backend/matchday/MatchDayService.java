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

    private void setDependants(MatchDayDto matchDayDto, MatchDay matchDay) {
        Round round = roundRepository.findById(matchDayDto.getRoundId())
            .orElseThrow(() -> new RoundNotFoundException(matchDayDto.getRoundId()));
        matchDay.setRound(round);
        Location location = locationRepository.findById(matchDayDto.getLocationId())
            .orElseThrow(() -> new LocationNotFoundException(matchDayDto.getLocationId()));
        matchDay.setLocation(location);
        Team teamAway = teamRepository.findById(matchDayDto.getTeamAwayId())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamAwayId()));
        matchDay.setTeamAway(teamAway);
        Team teamHome = teamRepository.findById(matchDayDto.getTeamHomeId())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamHomeId()));
        matchDay.setTeamHome(teamHome);
    }
}
