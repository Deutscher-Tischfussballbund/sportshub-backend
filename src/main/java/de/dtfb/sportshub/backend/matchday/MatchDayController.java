package de.dtfb.sportshub.backend.matchday;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/matchdays")
public class MatchDayController {

    private final MatchDayService service;

    public MatchDayController(MatchDayService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchDayDto> getAllMatchDays() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canOrganizeRound(#matchDayDto.roundId)")
    public ResponseEntity<MatchDayDto> createMatchDay(@RequestBody MatchDayDto matchDayDto) {
        MatchDayDto returnedDto = service.create(matchDayDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public MatchDayDto getMatchDay(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeMatchDay(#id)")
    public MatchDayDto updateMatchDay(@PathVariable String id, @RequestBody MatchDayDto matchDayDto) {
        return service.update(id, matchDayDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canOrganizeMatchDay(#id)")
    public void deleteMatchDay(@PathVariable String id) {
        service.delete(id);
    }

    @PostMapping("/{id}/result")
    @PreAuthorize("@authz.canReportMatchDay(#id)")
    public MatchDayDto submitResult(
            @PathVariable String id,
            @RequestBody MatchDayResultRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.submitResult(id, request, dtfbId);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize("@authz.canReportMatchDay(#id)")
    public MatchDayDto confirmResult(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.confirmResult(id, dtfbId);
    }
}
