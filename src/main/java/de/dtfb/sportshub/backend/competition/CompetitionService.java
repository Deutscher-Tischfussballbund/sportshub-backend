package de.dtfb.sportshub.backend.competition;

import de.dtfb.sportshub.backend.season.Season;
import de.dtfb.sportshub.backend.season.SeasonNotFoundException;
import de.dtfb.sportshub.backend.season.SeasonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CompetitionService {
    private final CompetitionRepository repository;
    private final CompetitionMapper mapper;
    private final SeasonRepository seasonRepository;

    public CompetitionService(CompetitionRepository repository, CompetitionMapper mapper, SeasonRepository seasonRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.seasonRepository = seasonRepository;
    }

    @Transactional(readOnly = true)
    public List<CompetitionDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public CompetitionDto get(String id) {
        Competition event = repository.findById(id).orElseThrow(
            () -> new CompetitionNotFoundException(id));
        return mapper.toDto(event);
    }

    @Transactional
    public CompetitionDto create(CompetitionDto competitionDto) {
        Competition event = mapper.toEntity(competitionDto);

        Season season = seasonRepository.findById(competitionDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(competitionDto.getSeasonId()));
        event.setSeason(season);

        Competition savedCompetition = repository.save(event);
        return mapper.toDto(savedCompetition);
    }

    @Transactional
    public CompetitionDto update(String id, CompetitionDto competitionDto) {
        Competition event = repository.findById(id).orElseThrow(
            () -> new CompetitionNotFoundException(id));

        mapper.updateEntityFromDto(competitionDto, event);

        Season season = seasonRepository.findById(competitionDto.getSeasonId())
            .orElseThrow(() -> new SeasonNotFoundException(competitionDto.getSeasonId()));
        event.setSeason(season);

        Competition savedCompetition = repository.save(event);
        return mapper.toDto(savedCompetition);
    }

    @Transactional
    public void delete(String id) {
        Competition event = repository.findById(id).orElseThrow(
            () -> new CompetitionNotFoundException(id));
        repository.delete(event);
    }
}
