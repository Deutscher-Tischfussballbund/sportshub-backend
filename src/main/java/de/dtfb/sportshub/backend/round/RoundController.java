package de.dtfb.sportshub.backend.round;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/rounds")
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
    @PreAuthorize("@authz.canOrganizePool(#roundDto.poolId)")
    public ResponseEntity<RoundDto> create(@RequestBody RoundDto roundDto) {
        RoundDto returnedDto = service.create(roundDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public RoundDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeRound(#id)")
    public RoundDto update(@PathVariable String id, @RequestBody RoundDto roundDto) {
        return service.update(id, roundDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeRound(#id)")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
