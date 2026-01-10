package de.dtfb.sportshub.backend.matchset;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matchsets")
public class MatchSetController {

    private final MatchSetService service;

    public MatchSetController(MatchSetService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchSetDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<MatchSetDto> create(@RequestBody MatchSetDto matchSetDto) {
        MatchSetDto returnedDto = service.create(matchSetDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId().toString()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public MatchSetDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public MatchSetDto update(@PathVariable String uuid, @RequestBody MatchSetDto matchSetDto) {
        return service.update(uuid, matchSetDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }
}
