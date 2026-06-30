package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SeasonService {
    private final SeasonRepository repository;
    private final SeasonMapper mapper;
    private final FederationRepository federationRepository;

    public SeasonService(SeasonRepository repository, SeasonMapper mapper, FederationRepository federationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
    }

    @Transactional(readOnly = true)
    public List<SeasonDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public SeasonDto get(String id) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));
        return mapper.toDto(season);
    }

    @Transactional
    public SeasonDto create(SeasonDto seasonDto) {
        Season newSeason = mapper.toEntity(seasonDto);

        Federation federation = federationRepository.findById(seasonDto.getFederationId())
            .orElseThrow(() -> new FederationNotFoundException(seasonDto.getFederationId()));
        newSeason.setFederation(federation);

        Season savedSeason = repository.save(newSeason);
        return mapper.toDto(savedSeason);
    }

    @Transactional
    public SeasonDto update(String id, SeasonDto seasonDto) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));

        mapper.updateEntityFromDto(seasonDto, season);

        Federation federation = federationRepository.findById(seasonDto.getFederationId())
            .orElseThrow(() -> new FederationNotFoundException(seasonDto.getFederationId()));
        season.setFederation(federation);

        Season savedSeason = repository.save(season);
        return mapper.toDto(savedSeason);
    }

    @Transactional
    public void delete(String id) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));
        repository.delete(season);
    }
}
