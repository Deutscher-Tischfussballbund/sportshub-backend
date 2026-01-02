package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchdayNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchsetService {
    private final MatchsetRepository repository;
    private final MatchsetMapper mapper;
    private final MatchRepository matchRepository;

    public MatchsetService(MatchsetRepository repository, MatchsetMapper mapper, MatchRepository matchRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.matchRepository = matchRepository;
    }

    List<MatchsetDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchsetDto get(String uuid) {
        Matchset matchset = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchsetNotFoundException(uuid));
        return mapper.toDto(matchset);
    }

    MatchsetDto create(MatchsetDto setDto) {
        Matchset matchset = mapper.toEntity(setDto);
        matchset.setUuid(UUID.randomUUID());

        Match match = matchRepository.findByUuid(setDto.getMatchUuid())
            .orElseThrow(() -> new MatchdayNotFoundException(setDto.getMatchUuid().toString()));
        matchset.setMatch(match);

        Matchset savedSet = repository.save(matchset);
        return mapper.toDto(savedSet);
    }

    MatchsetDto update(String uuid, MatchsetDto setDto) {
        Matchset matchset = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchsetNotFoundException(uuid));

        mapper.updateEntityFromDto(setDto, matchset);

        Match match = matchRepository.findByUuid(setDto.getMatchUuid())
            .orElseThrow(() -> new MatchdayNotFoundException(setDto.getMatchUuid().toString()));
        matchset.setMatch(match);

        Matchset savedSet = repository.save(matchset);
        return mapper.toDto(savedSet);
    }

    @Transactional
    void delete(String uuid) {
        Matchset matchset = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchsetNotFoundException(uuid));
        repository.delete(matchset);
    }
}
