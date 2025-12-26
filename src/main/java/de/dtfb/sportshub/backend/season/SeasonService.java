package de.dtfb.sportshub.backend.season;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeasonService {
    private final SeasonRepository repository;
    private final SeasonMapper mapper;

    public SeasonService(SeasonRepository repository, SeasonMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    List<SeasonDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    SeasonDto get(String uuid) {
        Season season = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));
        return mapper.toDto(season);
    }

    SeasonDto create(SeasonDto seasonDto) {
        Season newSeason = mapper.toEntity(seasonDto);
        newSeason.setUuid(UUID.randomUUID());
        Season savedSeason = repository.save(newSeason);
        return mapper.toDto(savedSeason);
    }

    SeasonDto update(String uuid, SeasonDto seasonDto) {
        Season season = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));

        mapper.updateEntityFromDto(seasonDto, season);

        Season savedSeason = repository.save(season);
        return mapper.toDto(savedSeason);
    }

    @Transactional
    void delete(String uuid) {
        Season season = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(uuid));
        repository.delete(season);
    }
}
