package de.dtfb.sportshub.backend.matchset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matchsets")
public class MatchsetController {

    private final MatchsetService service;

    public MatchsetController(MatchsetService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchsetDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<MatchsetDto> create(@RequestBody MatchsetDto matchsetDto) {
        MatchsetDto returnedDto = service.create(matchsetDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public MatchsetDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public MatchsetDto update(@PathVariable String uuid, @RequestBody MatchsetDto matchsetDto) {
        return service.update(uuid, matchsetDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
