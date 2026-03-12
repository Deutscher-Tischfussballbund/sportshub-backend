package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    List<SeasonDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    SeasonDto get(String uuid) {
        Season season = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));
        return mapper.toDto(season);
    }

    @Transactional
    SeasonDto create(SeasonDto seasonDto) {
        Season newSeason = mapper.toEntity(seasonDto);

        Federation federation = federationRepository.findById(seasonDto.getFederationId())
            .orElseThrow(() -> new FederationNotFoundException(seasonDto.getFederationId().toString()));
        newSeason.setFederation(federation);

        Season savedSeason = repository.save(newSeason);
        return mapper.toDto(savedSeason);
    }

    @Transactional
    SeasonDto update(String uuid, SeasonDto seasonDto) {
        Season season = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));

        mapper.updateEntityFromDto(seasonDto, season);

        Federation federation = federationRepository.findById(seasonDto.getFederationId())
            .orElseThrow(() -> new SeasonNotFoundException(seasonDto.getFederationId().toString()));
        season.setFederation(federation);

        Season savedSeason = repository.save(season);
        return mapper.toDto(savedSeason);
    }

    @Transactional
    void delete(String uuid) {
        Season season = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));
        repository.delete(season);
    }
}
