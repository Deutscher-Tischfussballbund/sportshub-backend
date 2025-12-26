package de.dtfb.sportshub.backend.location;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LocationService {
    private final LocationRepository repository;
    private final LocationMapper mapper;

    public LocationService(LocationRepository repository, LocationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    List<LocationDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    LocationDto get(String uuid) {
        Location location = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new LocationNotFoundException(uuid));
        return mapper.toDto(location);
    }

    LocationDto create(LocationDto locationDto) {
        Location newLocation = mapper.toEntity(locationDto);
        newLocation.setUuid(UUID.randomUUID());
        Location savedLocation = repository.save(newLocation);
        return mapper.toDto(savedLocation);
    }

    LocationDto update(String uuid, LocationDto locationDto) {
        Location location = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new LocationNotFoundException(uuid));

        mapper.updateEntityFromDto(locationDto, location);

        Location savedLocation = repository.save(location);
        return mapper.toDto(savedLocation);
    }

    @Transactional
    void delete(String uuid) {
        Location location = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new LocationNotFoundException(uuid));
        repository.delete(location);
    }
}
