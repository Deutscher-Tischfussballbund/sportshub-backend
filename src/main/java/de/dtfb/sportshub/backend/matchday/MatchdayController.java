package de.dtfb.sportshub.backend.matchday;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matchdays")
public class MatchdayController {

    private final MatchdayService service;

    public MatchdayController(MatchdayService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchdayDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<MatchdayDto> create(@RequestBody MatchdayDto matchdayDto) {
        MatchdayDto returnedDto = service.create(matchdayDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getUuid().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public MatchdayDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public MatchdayDto update(@PathVariable String uuid, @RequestBody MatchdayDto matchdayDto) {
        return service.update(uuid, matchdayDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
