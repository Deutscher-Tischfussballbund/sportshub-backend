package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.round.RoundNotFoundException;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchEventService {
    private final MatchEventRepository repository;
    private final MatchEventMapper mapper;
    private final MatchRepository matchRepository;
    private final TeamRepository teamRepository;

    public MatchEventService(MatchEventRepository repository, MatchEventMapper mapper, MatchRepository matchRepository, TeamRepository teamRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.matchRepository = matchRepository;
        this.teamRepository = teamRepository;
    }

    List<MatchEventDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchEventDto get(String uuid) {
        MatchEvent matchEvent = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchEventNotFoundException(uuid));
        return mapper.toDto(matchEvent);
    }

    MatchEventDto create(MatchEventDto matchEventDto) {
        MatchEvent matchEvent = mapper.toEntity(matchEventDto);

        setDependants(matchEventDto, matchEvent);

        MatchEvent savedMatchEvent = repository.save(matchEvent);
        return mapper.toDto(savedMatchEvent);
    }

    MatchEventDto update(String uuid, MatchEventDto matchEventDto) {
        MatchEvent matchEvent = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchEventNotFoundException(uuid));

        mapper.updateEntityFromDto(matchEventDto, matchEvent);

        setDependants(matchEventDto, matchEvent);

        MatchEvent savedMatchEvent = repository.save(matchEvent);
        return mapper.toDto(savedMatchEvent);
    }

    @Transactional
    void delete(String uuid) {
        MatchEvent matchEvent = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchEventNotFoundException(uuid));
        repository.delete(matchEvent);
    }

    private void setDependants(MatchEventDto matchEventDto, MatchEvent matchEvent) {
        UUID matchId = matchEventDto.getMatchId();
        if (matchId != null && !matchId.equals(matchEvent.getId())) {
            Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new RoundNotFoundException(matchId.toString()));
            matchEvent.setMatch(match);
        }
        Team team = teamRepository.findById(matchEventDto.getTeamId())
            .orElseThrow(() -> new TeamNotFoundException(matchEventDto.getTeamId().toString()));
        matchEvent.setTeam(team);
    }
}
