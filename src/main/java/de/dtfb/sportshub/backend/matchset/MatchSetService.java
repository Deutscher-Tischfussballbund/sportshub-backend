package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchRepository;
import de.dtfb.sportshub.backend.matchday.MatchDayNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchSetService {
    private final MatchSetRepository repository;
    private final MatchSetMapper mapper;
    private final MatchRepository matchRepository;

    public MatchSetService(MatchSetRepository repository, MatchSetMapper mapper, MatchRepository matchRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.matchRepository = matchRepository;
    }

    List<MatchSetDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchSetDto get(String uuid) {
        MatchSet matchSet = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchSetNotFoundException(uuid));
        return mapper.toDto(matchSet);
    }

    MatchSetDto create(MatchSetDto setDto) {
        MatchSet matchSet = mapper.toEntity(setDto);

        Match match = matchRepository.findById(setDto.getMatchId())
            .orElseThrow(() -> new MatchDayNotFoundException(setDto.getMatchId().toString()));
        matchSet.setMatch(match);

        MatchSet savedSet = repository.save(matchSet);
        return mapper.toDto(savedSet);
    }

    MatchSetDto update(String uuid, MatchSetDto setDto) {
        MatchSet matchSet = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchSetNotFoundException(uuid));

        mapper.updateEntityFromDto(setDto, matchSet);

        Match match = matchRepository.findById(setDto.getMatchId())
            .orElseThrow(() -> new MatchDayNotFoundException(setDto.getMatchId().toString()));
        matchSet.setMatch(match);

        MatchSet savedSet = repository.save(matchSet);
        return mapper.toDto(savedSet);
    }

    @Transactional
    void delete(String uuid) {
        MatchSet matchSet = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchSetNotFoundException(uuid));
        repository.delete(matchSet);
    }
}
