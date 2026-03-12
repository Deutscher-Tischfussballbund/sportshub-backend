package de.dtfb.sportshub.backend.federation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
public class FederationController {

    private final FederationService service;

    public FederationController(FederationService service) {
        this.service = service;
    }

    @GetMapping
    public List<FederationDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<FederationDto> create(@RequestBody FederationDto eventDto) {
        FederationDto returnedDto = service.create(eventDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public FederationDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public FederationDto update(@PathVariable String uuid, @RequestBody FederationDto eventDto) {
        return service.update(uuid, eventDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
