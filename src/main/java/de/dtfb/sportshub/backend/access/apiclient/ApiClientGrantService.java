package de.dtfb.sportshub.backend.access.apiclient;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class ApiClientGrantService {
    private final ApiClientGrantRepository repository;
    private final ApiClientGrantMapper mapper;

    public ApiClientGrantService(ApiClientGrantRepository repository, ApiClientGrantMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ApiClientGrantDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional
    public ApiClientGrantDto create(ApiClientGrantDto dto) {
        ApiClientGrant grant = mapper.toEntity(dto);
        grant.setCreatedAt(Instant.now());

        return mapper.toDto(repository.save(grant));
    }

    @Transactional
    public ApiClientGrantDto update(String id, ApiClientGrantDto dto) {
        ApiClientGrant grant = getGrant(id);

        mapper.updateEntityFromDto(dto, grant);

        return mapper.toDto(repository.save(grant));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getGrant(id));
    }

    private @NonNull ApiClientGrant getGrant(String id) {
        return repository.findById(id).orElseThrow(
            () -> new ApiClientGrantNotFoundException(id));
    }
}
