package de.dtfb.sportshub.backend.season;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/seasons")
public class SeasonController {

    private final SeasonService service;

    public SeasonController(SeasonService service) {
        this.service = service;
    }

    @GetMapping
    public List<SeasonDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<SeasonDto> create(@RequestBody SeasonDto seasonDto) {
        SeasonDto returnedDto = service.create(seasonDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public SeasonDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public SeasonDto update(@PathVariable String uuid, @RequestBody SeasonDto seasonDto) {
        return service.update(uuid, seasonDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
