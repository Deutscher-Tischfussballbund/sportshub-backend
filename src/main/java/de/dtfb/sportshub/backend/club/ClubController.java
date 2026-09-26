package de.dtfb.sportshub.backend.club;

import de.dtfb.sportshub.backend.history.EntityHistoryDto;
import de.dtfb.sportshub.backend.history.EntityHistoryService;
import de.dtfb.sportshub.backend.history.HistoryEntityType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
public class ClubController {

    private final ClubService service;
    private final EntityHistoryService historyService;

    public ClubController(ClubService service, EntityHistoryService historyService) {
        this.service = service;
        this.historyService = historyService;
    }

    @GetMapping("/v1/clubs")
    public List<ClubDto> clubs() {
        return service.getAll();
    }

    @PostMapping("/v1/clubs")
    @PreAuthorize("@authz.canManageRegion(#dto.regionId)")
    public ResponseEntity<ClubDto> createClub(@RequestBody ClubDto dto) {
        ClubDto created = service.create(dto);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + created.id()).build().toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/v1/clubs/{id}")
    @PreAuthorize("@authz.canManageClub(#id)")
    public ClubDto updateClub(@PathVariable String id, @RequestBody ClubDto dto, @AuthenticationPrincipal Jwt jwt) {
        return service.update(id, dto, jwt.getClaimAsString("dtfb_id"));
    }

    @DeleteMapping("/v1/clubs/{id}")
    @PreAuthorize("@authz.canManageClub(#id)")
    public void deleteClub(@PathVariable String id) {
        service.delete(id);
    }

    @GetMapping("/v1/clubs/{id}/history")
    @PreAuthorize("@authz.canManageClub(#id)")
    public List<EntityHistoryDto> clubHistory(@PathVariable String id) {
        return historyService.history(HistoryEntityType.CLUB, id);
    }
}
