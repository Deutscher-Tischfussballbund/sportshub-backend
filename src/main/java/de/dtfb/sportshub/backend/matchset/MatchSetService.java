package de.dtfb.sportshub.backend.matchset;

import de.dtfb.sportshub.backend.match.Match;
import de.dtfb.sportshub.backend.match.MatchNotFoundException;
import de.dtfb.sportshub.backend.match.MatchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<MatchSetDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public MatchSetDto get(String id) {
        MatchSet matchSet = repository.findVisibleById(id).orElseThrow(
            () -> new MatchSetNotFoundException(id));
        return mapper.toDto(matchSet);
    }

    @Transactional
    public MatchSetDto create(MatchSetDto setDto) {
        MatchSet matchSet = mapper.toEntity(setDto);

        Match match = matchRepository.findById(setDto.getMatchId())
            .orElseThrow(() -> new MatchNotFoundException(setDto.getMatchId()));
        matchSet.setMatch(match);

        MatchSet savedSet = repository.save(matchSet);
        return mapper.toDto(savedSet);
    }

    @Transactional
    public MatchSetDto update(String id, MatchSetDto setDto) {
        MatchSet matchSet = repository.findById(id).orElseThrow(
            () -> new MatchSetNotFoundException(id));

        mapper.updateEntityFromDto(setDto, matchSet);

        Match match = matchRepository.findById(setDto.getMatchId())
            .orElseThrow(() -> new MatchNotFoundException(setDto.getMatchId()));
        matchSet.setMatch(match);

        MatchSet savedSet = repository.save(matchSet);
        return mapper.toDto(savedSet);
    }

    @Transactional
    public void delete(String id) {
        MatchSet matchSet = repository.findById(id).orElseThrow(
            () -> new MatchSetNotFoundException(id));
        repository.delete(matchSet);
    }
}
