package de.dtfb.sportshub.backend.season;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SeasonService {
    private final SeasonRepository repository;
    private final SeasonMapper mapper;
    private final FederationRepository federationRepository;
    private final SeasonStructure structure;

    public SeasonService(SeasonRepository repository, SeasonMapper mapper,
                         FederationRepository federationRepository, SeasonStructure structure) {
        this.repository = repository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
        this.structure = structure;
    }

    /** Active (non-archived) seasons only. */
    @Transactional(readOnly = true)
    public List<SeasonDto> getAll() {
        return mapper.toDtoList(repository.findByArchivedAtIsNull());
    }

    /** Archived (soft-deleted) seasons, for an explicit archive view. */
    @Transactional(readOnly = true)
    public List<SeasonDto> getArchived() {
        return mapper.toDtoList(repository.findByArchivedAtIsNotNull());
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

    /** Archive (reversible soft-delete): hide the season but keep all data. Idempotent. */
    @Transactional
    public SeasonDto archive(String id) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));
        if (season.getArchivedAt() == null) {
            season.setArchivedAt(Instant.now());
        }
        return mapper.toDto(repository.save(season));
    }

    /** Restore an archived season. */
    @Transactional
    public SeasonDto unarchive(String id) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));
        season.setArchivedAt(null);
        return mapper.toDto(repository.save(season));
    }

    /**
     * Hard delete — permitted only when the season holds no recorded results. A season with played
     * results is refused ({@link SeasonDeletionBlockedException} → 409); the caller archives instead.
     * Otherwise the (result-free) competition structure is wiped leaf→root in this transaction.
     */
    @Transactional
    public void delete(String id) {
        Season season = repository.findById(id).orElseThrow(
            () -> new SeasonNotFoundException(id));

        SeasonContents contents = structure.contentsOf(id);
        if (contents.hasResults()) {
            throw new SeasonDeletionBlockedException(contents);
        }

        structure.deleteStructure(id);
        repository.delete(season);
    }
}
