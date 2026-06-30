package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayNotFoundException;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MatchService {
    private final MatchRepository repository;
    private final MatchMapper mapper;
    private final MatchDayRepository matchDayRepository;

    public MatchService(MatchRepository repository, MatchMapper mapper, MatchDayRepository matchDayRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.matchDayRepository = matchDayRepository;
    }

    @Transactional(readOnly = true)
    public List<MatchDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public MatchDto get(String id) {
        Match match = repository.findById(id).orElseThrow(
            () -> new MatchNotFoundException(id));
        return mapper.toDto(match);
    }

    @Transactional
    public MatchDto create(MatchDto matchDto) {
        Match match = mapper.toEntity(matchDto);

        MatchDay matchDay = matchDayRepository.findById(matchDto.getMatchDayId())
            .orElseThrow(() -> new MatchDayNotFoundException(matchDto.getMatchDayId()));
        match.setMatchDay(matchDay);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    @Transactional
    public MatchDto update(String id, MatchDto matchDto) {
        Match match = repository.findById(id).orElseThrow(
            () -> new MatchNotFoundException(id));

        mapper.updateEntityFromDto(matchDto, match);

        MatchDay matchDay = matchDayRepository.findById(matchDto.getMatchDayId())
            .orElseThrow(() -> new MatchDayNotFoundException(matchDto.getMatchDayId()));
        match.setMatchDay(matchDay);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    @Transactional
    public void delete(String id) {
        Match match = repository.findById(id).orElseThrow(
            () -> new MatchNotFoundException(id));
        repository.delete(match);
    }
}
