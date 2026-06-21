package de.dtfb.sportshub.backend.federation;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/federation")
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
    public ResponseEntity<FederationDto> create(@RequestBody FederationDto federationDto) {
        FederationDto returnedDto = service.create(federationDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public FederationDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public FederationDto update(@PathVariable String id, @RequestBody FederationDto federationDto) {
        return service.update(id, federationDto);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
