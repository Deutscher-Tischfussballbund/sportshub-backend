package de.dtfb.sportshub.backend.matchevent;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchNotFoundException;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<MatchEventDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public MatchEventDto get(String id) {
        MatchEvent matchEvent = repository.findVisibleById(id).orElseThrow(
            () -> new MatchEventNotFoundException(id));
        return mapper.toDto(matchEvent);
    }

    @Transactional
    public MatchEventDto create(MatchEventDto matchEventDto) {
        MatchEvent matchEvent = mapper.toEntity(matchEventDto);

        setDependants(matchEventDto, matchEvent);

        MatchEvent savedMatchEvent = repository.save(matchEvent);
        return mapper.toDto(savedMatchEvent);
    }

    @Transactional
    public MatchEventDto update(String id, MatchEventDto matchEventDto) {
        MatchEvent matchEvent = repository.findById(id).orElseThrow(
            () -> new MatchEventNotFoundException(id));

        mapper.updateEntityFromDto(matchEventDto, matchEvent);

        setDependants(matchEventDto, matchEvent);

        MatchEvent savedMatchEvent = repository.save(matchEvent);
        return mapper.toDto(savedMatchEvent);
    }

    @Transactional
    public void delete(String id) {
        MatchEvent matchEvent = repository.findById(id).orElseThrow(
            () -> new MatchEventNotFoundException(id));
        repository.delete(matchEvent);
    }

    private void setDependants(MatchEventDto matchEventDto, MatchEvent matchEvent) {
        String matchId = matchEventDto.getMatchId();
        if (matchId != null && !matchId.equals(matchEvent.getId())) {
            Match match = matchRepository.findById(matchId)
                .orElseThrow(() -> new MatchNotFoundException(matchId));
            matchEvent.setMatch(match);
        }
        Team team = teamRepository.findById(matchEventDto.getTeamId())
            .orElseThrow(() -> new TeamNotFoundException(matchEventDto.getTeamId()));
        matchEvent.setTeam(team);
    }
}
