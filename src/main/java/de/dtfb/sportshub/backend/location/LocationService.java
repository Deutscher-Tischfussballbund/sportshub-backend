package de.dtfb.sportshub.backend.location;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class LocationService {
    private final LocationRepository repository;
    private final LocationMapper mapper;

    public LocationService(LocationRepository repository, LocationMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
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
        Location savedLocation = repository.save(newLocation);
        return mapper.toDto(savedLocation);
    }

    @Transactional
    public LocationDto update(String id, LocationDto locationDto) {
        Location location = repository.findById(id).orElseThrow(
            () -> new LocationNotFoundException(id));

        mapper.updateEntityFromDto(locationDto, location);

        Location savedLocation = repository.save(location);
        return mapper.toDto(savedLocation);
    }

    @Transactional
    public void delete(String id) {
        Location location = repository.findById(id).orElseThrow(
            () -> new LocationNotFoundException(id));
        repository.delete(location);
    }
}
