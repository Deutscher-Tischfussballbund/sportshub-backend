package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.Matchday;
import de.dtfb.sportshub.backend.matchday.MatchdayNotFoundException;
import de.dtfb.sportshub.backend.matchday.MatchdayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchService {
    private final MatchRepository repository;
    private final MatchMapper mapper;
    private final MatchdayRepository matchdayRepository;

    public MatchService(MatchRepository repository, MatchMapper mapper, MatchdayRepository matchdayRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.matchdayRepository = matchdayRepository;
    }

    List<MatchDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchDto get(String uuid) {
        Match match = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));
        return mapper.toDto(match);
    }

    MatchDto create(MatchDto matchDto) {
        Match match = mapper.toEntity(matchDto);
        match.setUuid(UUID.randomUUID());

        Matchday matchday = matchdayRepository.findByUuid(matchDto.getMatchdayUuid())
            .orElseThrow(() -> new MatchdayNotFoundException(matchDto.getMatchdayUuid().toString()));
        match.setMatchday(matchday);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    MatchDto update(String uuid, MatchDto matchDto) {
        Match match = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));

        mapper.updateEntityFromDto(matchDto, match);

        Matchday matchday = matchdayRepository.findByUuid(matchDto.getMatchdayUuid())
            .orElseThrow(() -> new MatchdayNotFoundException(matchDto.getMatchdayUuid().toString()));
        match.setMatchday(matchday);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    @Transactional
    void delete(String uuid) {
        Match match = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));
        repository.delete(match);
    }
}
