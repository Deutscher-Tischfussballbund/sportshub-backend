package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {
    private final LocationRepository repository;
    private final LocationMapper mapper;
    private final FederationRepository federationRepository;

    public LocationService(LocationRepository repository, LocationMapper mapper,
                           FederationRepository federationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
    }

    @Transactional(readOnly = true)
    public List<LocationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public LocationDto get(String id) {
        Location location = repository.findById(id).orElseThrow(
            () -> new LocationNotFoundException(id));
        return mapper.toDto(location);
    }

    @Transactional
    public LocationDto create(LocationDto locationDto) {
        Location newLocation = mapper.toEntity(locationDto);
        resolveFederation(locationDto, newLocation);
        Location savedLocation = repository.save(newLocation);
        return mapper.toDto(savedLocation);
    }

    @Transactional
    public LocationDto update(String id, LocationDto locationDto) {
        Location location = repository.findById(id).orElseThrow(
            () -> new LocationNotFoundException(id));

        mapper.updateEntityFromDto(locationDto, location);
        resolveFederation(locationDto, location);

        Location savedLocation = repository.save(location);
        return mapper.toDto(savedLocation);
    }

    /** Attach the owning region if one was supplied; a region-less location is a global venue. */
    private void resolveFederation(LocationDto dto, Location location) {
        if (dto.getFederationId() != null) {
            Federation federation = federationRepository.findById(dto.getFederationId())
                .orElseThrow(() -> new FederationNotFoundException(dto.getFederationId()));
            location.setFederation(federation);
        }
    }

    @Transactional
    public void delete(String id) {
        Location location = repository.findById(id).orElseThrow(
            () -> new LocationNotFoundException(id));
        repository.delete(location);
    }
}
