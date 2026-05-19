package de.dtfb.sportshub.backend.federation;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FederationService {
    private final FederationRepository repository;
    private final FederationMapper mapper;

    public FederationService(FederationRepository repository, FederationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    List<FederationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    FederationDto get(String uuid) {
        Federation federation = repository.findById(uuid).orElseThrow(
            () -> new FederationNotFoundException(uuid));
        return mapper.toDto(federation);
    }

    @Transactional
    FederationDto create(FederationDto federationDto) {
        Federation federation = mapper.toEntity(federationDto);

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    @Transactional
    FederationDto update(String uuid, FederationDto federationDto) {
        Federation federation = repository.findById(uuid).orElseThrow(
            () -> new FederationNotFoundException(uuid));

        mapper.updateEntityFromDto(federationDto, federation);

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    @Transactional
    void delete(String uuid) {
        Federation federation = repository.findById(uuid).orElseThrow(
            () -> new FederationNotFoundException(uuid));
        repository.delete(federation);
    }
}
