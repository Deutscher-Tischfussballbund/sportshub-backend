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

    @Transactional(readOnly = true)
    public List<FederationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public FederationDto get(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        return mapper.toDto(federation);
    }

    @Transactional
    public FederationDto create(FederationDto federationDto) {
        Federation federation = mapper.toEntity(federationDto);

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    @Transactional
    public FederationDto update(String id, FederationDto federationDto) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));

        mapper.updateEntityFromDto(federationDto, federation);

        Federation savedFederation = repository.save(federation);
        return mapper.toDto(savedFederation);
    }

    @Transactional
    public void delete(String id) {
        Federation federation = repository.findById(id).orElseThrow(
            () -> new FederationNotFoundException(id));
        repository.delete(federation);
    }
}
