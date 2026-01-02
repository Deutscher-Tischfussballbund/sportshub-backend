package de.dtfb.sportshub.backend.phase;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/phases")
public class PhaseController {

    private final PhaseService service;

    public PhaseController(PhaseService service) {
        this.service = service;
    }

    @GetMapping
    public List<PhaseDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<PhaseDto> create(@Valid @RequestBody PhaseDto phaseDto) {
        PhaseDto returnedDto = service.create(phaseDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public PhaseDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public PhaseDto update(@PathVariable String uuid, @Valid @RequestBody PhaseDto phaseDto) {
        return service.update(uuid, phaseDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
