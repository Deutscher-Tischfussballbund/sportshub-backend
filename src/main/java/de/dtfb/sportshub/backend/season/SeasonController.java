package de.dtfb.sportshub.backend.season;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/seasons")
public class SeasonController {

    private final SeasonService service;

    public SeasonController(SeasonService service) {
        this.service = service;
    }

    @GetMapping
    public List<SeasonDto> getAll() {
        return service.getAll();
    }

    @GetMapping("/archived")
    public List<SeasonDto> getArchived() {
        return service.getArchived();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageRegion(#seasonDto.federationId)")
    public ResponseEntity<SeasonDto> create(@RequestBody SeasonDto seasonDto) {
        SeasonDto returnedDto = service.create(seasonDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public SeasonDto get(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageSeason(#id)")
    public SeasonDto update(@PathVariable String id, @RequestBody SeasonDto seasonDto) {
        return service.update(id, seasonDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageSeason(#id)")
    @ApiResponse(responseCode = "204", description = "Season deleted")
    @ApiResponse(responseCode = "409", description = "Season has recorded results — archive it instead",
        content = @Content(schema = @Schema(implementation = SeasonDeletionBlockedError.class)))
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("@authz.canManageSeason(#id)")
    public SeasonDto archive(@PathVariable String id) {
        return service.archive(id);
    }

    @PostMapping("/{id}/unarchive")
    @PreAuthorize("@authz.canManageSeason(#id)")
    public SeasonDto unarchive(@PathVariable String id) {
        return service.unarchive(id);
    }
}
