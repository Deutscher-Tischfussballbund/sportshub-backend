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
    private final MatchDayResultService resultService;

    public MatchDayController(MatchDayService service, MatchDayResultService resultService) {
        this.service = service;
        this.resultService = resultService;
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

    /**
     * Entered, not yet final results the current user has to act on -- as a captain of a side (the
     * countdown banner) or as a neutral admin (the league admin's overview), overdue first (docs/22).
     */
    @GetMapping("/pending-results")
    public List<MatchDayResultDto> getPendingResults() {
        return resultService.pending();
    }

    /** The fixture's result for the result screen, incl. what the current user may do (docs/17). */
    @GetMapping("/{id}/result")
    public MatchDayResultDto getResult(@PathVariable String id) {
        return resultService.get(id);
    }

    /** Enters or edits the result: a team member of either side, or a neutral admin (docs/17). */
    @PostMapping("/{id}/result")
    @PreAuthorize("@authz.canEnterResult(#id)")
    public MatchDayResultDto enterResult(
            @PathVariable String id,
            @RequestBody MatchDayResultRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return resultService.enter(id, request, jwt.getClaimAsString("dtfb_id"));
    }

    /** A captain agrees for their side, or a neutral admin finalizes the result (docs/17). */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("@authz.canConfirmResult(#id)")
    public MatchDayResultDto confirmResult(@PathVariable String id) {
        return resultService.confirm(id);
    }

    @PostMapping("/{id}/schedule/propose")
    @PreAuthorize("@authz.canReportMatchDay(#id)")
    public MatchDayDto proposeSchedule(
            @PathVariable String id,
            @RequestBody ScheduleProposalRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.proposeSchedule(id, request, dtfbId);
    }

    @PostMapping("/{id}/schedule/accept")
    @PreAuthorize("@authz.canReportMatchDay(#id)")
    public MatchDayDto acceptSchedule(
            @PathVariable String id,
            @AuthenticationPrincipal Jwt jwt) {
        String dtfbId = jwt.getClaimAsString("dtfb_id");
        return service.acceptSchedule(id, dtfbId);
    }
}
