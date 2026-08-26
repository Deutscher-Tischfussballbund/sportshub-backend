package de.dtfb.sportshub.backend.releasenotes;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * The functional "what's new" feed shown to every logged-in admin-app user (shell bell button) --
 * global admins author entries, everyone else only reads. Distinct from technical/dev release
 * notes, which are GitHub's own auto-generated notes on tagged releases (see release.yml).
 */
@RestController
@RequestMapping("/v1/release-notes")
public class ReleaseNoteController {

    private final ReleaseNoteService service;

    public ReleaseNoteController(ReleaseNoteService service) {
        this.service = service;
    }

    @GetMapping
    public List<ReleaseNoteDto> getAllReleaseNotes() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.isAdmin()")
    public ResponseEntity<ReleaseNoteDto> createReleaseNote(@RequestBody ReleaseNoteDto releaseNoteDto) {
        ReleaseNoteDto returnedDto = service.create(releaseNoteDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public ReleaseNoteDto updateReleaseNote(@PathVariable String id, @RequestBody ReleaseNoteDto releaseNoteDto) {
        return service.update(id, releaseNoteDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.isAdmin()")
    public void deleteReleaseNote(@PathVariable String id) {
        service.delete(id);
    }
}
