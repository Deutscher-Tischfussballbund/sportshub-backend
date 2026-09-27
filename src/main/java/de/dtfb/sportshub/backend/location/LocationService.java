package de.dtfb.sportshub.backend.location;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import de.dtfb.sportshub.backend.matchday.MatchDayRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

@Service
public class LocationService {
    private final LocationRepository repository;
    private final LocationMapper mapper;
    private final FederationRepository federationRepository;
    private final MatchDayRepository matchDayRepository;

    public LocationService(LocationRepository repository, LocationMapper mapper,
                           FederationRepository federationRepository, MatchDayRepository matchDayRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
        this.matchDayRepository = matchDayRepository;
    }

    /** All venues, or with {@code federationId} a region's own plus the global ones. */
    @Transactional(readOnly = true)
    public List<LocationDto> getAll(String federationId) {
        return mapper.toDtoList(federationId == null || federationId.isBlank()
            ? repository.findAll()
            : repository.findForRegion(federationId));
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

        // The region is fixed after creation: the edit right is checked against the CURRENT region
        // (canManageLocation), so accepting a new one would let an admin move venues across regions.
        String currentRegion = location.getFederation() == null ? null : location.getFederation().getId();
        if (locationDto.getFederationId() != null && !Objects.equals(locationDto.getFederationId(), currentRegion)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A venue's region can't be changed");
        }
        mapper.updateEntityFromDto(locationDto, location);

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
        long fixtures = matchDayRepository.countByLocationId(id);
        if (fixtures > 0) {
            throw new LocationDeletionBlockedException(fixtures);
        }
        repository.delete(location);
    }
}
