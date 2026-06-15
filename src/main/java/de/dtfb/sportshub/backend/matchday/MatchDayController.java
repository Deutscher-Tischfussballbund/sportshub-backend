package de.dtfb.sportshub.backend.matchday;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/matchdays")
public class MatchDayController {

    private final MatchDayService service;

    public MatchDayController(MatchDayService service) {
        this.service = service;
    }

    @GetMapping
    public List<MatchDayDto> getAll() {
        return service.getAll();
    }

    @PostMapping
    public ResponseEntity<MatchDayDto> create(@RequestBody MatchDayDto matchDayDto) {
        MatchDayDto returnedDto = service.create(matchDayDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{uuid}")
    public MatchDayDto get(@PathVariable String uuid) {
        return service.get(uuid);
    }

    @PutMapping("/{uuid}")
    public MatchDayDto update(@PathVariable String uuid, @RequestBody MatchDayDto matchDayDto) {
        return service.update(uuid, matchDayDto);
    }

    @DeleteMapping("/{uuid}")
    public void delete(@PathVariable String uuid) {
        service.delete(uuid);
    }

    @PostMapping("/{uuid}/result")
    public MatchDayDto submitResult(
            @PathVariable String uuid,
            @RequestBody MatchDayResultRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.submitResult(uuid, request, dtfbId);
    }

    @PostMapping("/{uuid}/confirm")
    public MatchDayDto confirmResult(
            @PathVariable String uuid,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.confirmResult(uuid, dtfbId);
    }
}
