package de.dtfb.sportshub.backend.matchset;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/matchsets")
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
    @PreAuthorize("@authz.canOrganizeMatch(#matchSetDto.matchId)")
    public ResponseEntity<MatchSetDto> create(@RequestBody MatchSetDto matchSetDto) {
        MatchSetDto returnedDto = service.create(matchSetDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public MatchSetDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeMatchSet(#id)")
    public MatchSetDto update(@PathVariable String id, @RequestBody MatchSetDto matchSetDto) {
        return service.update(id, matchSetDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeMatchSet(#id)")
    public void delete(@PathVariable String id) {
        service.delete(id);
    }
}
