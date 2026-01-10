package de.dtfb.sportshub.backend.match;

import de.dtfb.sportshub.backend.matchday.MatchDay;
import de.dtfb.sportshub.backend.matchday.MatchDayNotFoundException;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    List<MatchDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchDto get(String uuid) {
        Match match = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));
        return mapper.toDto(match);
    }

    MatchDto create(MatchDto matchDto) {
        Match match = mapper.toEntity(matchDto);

        MatchDay matchDay = matchDayRepository.findById(matchDto.getMatchDayId())
            .orElseThrow(() -> new MatchDayNotFoundException(matchDto.getMatchDayId().toString()));
        match.setMatchDay(matchDay);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    MatchDto update(String uuid, MatchDto matchDto) {
        Match match = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));

        mapper.updateEntityFromDto(matchDto, match);

        MatchDay matchDay = matchDayRepository.findById(matchDto.getMatchDayId())
            .orElseThrow(() -> new MatchDayNotFoundException(matchDto.getMatchDayId().toString()));
        match.setMatchDay(matchDay);

        Match savedMatch = repository.save(match);
        return mapper.toDto(savedMatch);
    }

    @Transactional
    void delete(String uuid) {
        Match match = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchNotFoundException(uuid));
        repository.delete(match);
    }
}
