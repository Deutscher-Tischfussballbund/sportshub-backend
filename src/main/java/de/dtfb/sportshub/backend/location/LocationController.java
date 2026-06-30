package de.dtfb.sportshub.backend.location;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/locations")
public class LocationController {

    private final LocationService service;

    public LocationController(LocationService service) {
        this.service = service;
    }

    @GetMapping
    public List<LocationDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageRegion(#locationDto.federationId)")
    public ResponseEntity<LocationDto> create(@RequestBody LocationDto locationDto) {
        LocationDto returnedDto = service.create(locationDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public LocationDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageLocation(#id)")
    public LocationDto update(@PathVariable String id, @RequestBody LocationDto locationDto) {
        return service.update(id, locationDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageLocation(#id)")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
