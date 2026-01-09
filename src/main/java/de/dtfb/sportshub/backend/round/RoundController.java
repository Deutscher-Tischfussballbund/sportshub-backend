package de.dtfb.sportshub.backend.round;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rounds")
public class RoundController {

    private final RoundService service;

    public RoundController(RoundService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoundDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<RoundDto> create(@RequestBody RoundDto roundDto) {
        RoundDto returnedDto = service.create(roundDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public RoundDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public RoundDto update(@PathVariable String uuid, @RequestBody RoundDto roundDto) {
        return service.update(uuid, roundDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
