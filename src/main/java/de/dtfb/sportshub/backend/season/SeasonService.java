package de.dtfb.sportshub.backend.season;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SeasonService {
    private final SeasonRepository seasonRepository;
    private final SeasonMapper seasonMapper;

    public SeasonService(SeasonRepository seasonRepository, SeasonMapper seasonMapper) {
        this.seasonRepository = seasonRepository;
        this.seasonMapper = seasonMapper;
    }

    List<SeasonDto> getAllSeasons() {
        return seasonMapper.toDtoList(seasonRepository.findAll());
    }

    SeasonDto createSeason(SeasonDto seasonDto) {
        Season newSeason = seasonMapper.toEntity(seasonDto);
        newSeason.setUuid(UUID.randomUUID());
        Season savedSeason = seasonRepository.save(newSeason);
        return seasonMapper.toDto(savedSeason);
    }

    SeasonDto updateSeason(String uuid, SeasonDto seasonDto) {
        Season season = seasonRepository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(UUID.fromString(uuid)));

        seasonMapper.updateEntityFromDto(seasonDto, season);
        Season savedSeason = seasonRepository.save(season);
        return seasonMapper.toDto(savedSeason);
    }

    @Transactional
    public void deleteSeason(String uuid) {
        seasonRepository.deleteByUuid(UUID.fromString(uuid));
    }

    public SeasonDto getSeason(String uuid) {
        Season season = seasonRepository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new SeasonNotFoundException(UUID.fromString(uuid)));
        return seasonMapper.toDto(season);
    }
}
