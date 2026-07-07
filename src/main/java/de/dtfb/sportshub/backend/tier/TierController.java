package de.dtfb.sportshub.backend.tier;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/tiers")
public class TierController {

    private final TierService service;

    public TierController(TierService service) {
        this.service = service;
    }

    @GetMapping
    public List<TierDto> getAllTiers() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageCompetition(#tierDto.competitionId)")
    public ResponseEntity<TierDto> createTier(@RequestBody TierDto tierDto) {
        TierDto returnedDto = service.create(tierDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public TierDto getTier(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageTier(#id)")
    public TierDto updateTier(@PathVariable String id, @RequestBody TierDto tierDto) {
        return service.update(id, tierDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageTier(#id)")
    public void deleteTier(@PathVariable String id) {
        service.delete(id);
    }
}
