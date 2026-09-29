package de.dtfb.sportshub.backend.lineup;

import de.dtfb.sportshub.backend.matchday.ResultActor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * A fixture's line-ups and substitutions (docs/23). Reads show each side as far as the current user
 * may see it; writes check captain/neutral-admin and the line-up rules in {@link LineupService}.
 */
@RestController
@RequestMapping("/v1/matchdays")
public class LineupController {

    private final LineupService service;

    public LineupController(LineupService service) {
        this.service = service;
    }

    @GetMapping("/{id}/lineups")
    public LineupsDto getLineups(@PathVariable String id) {
        return service.view(id);
    }

    /** Saves a side's line-up as a draft, or submits it ({@code submit: true}). */
    @PutMapping("/{id}/lineups/{side}")
    @PreAuthorize("@authz.canConfirmResult(#id)")
    public LineupsDto saveLineup(@PathVariable String id, @PathVariable ResultActor.Side side,
                                 @RequestBody LineupRequests.SaveLineup request,
                                 @AuthenticationPrincipal Jwt jwt) {
        return service.save(id, side, request, jwt.getClaimAsString("dtfb_id"));
    }

    @PostMapping("/{id}/substitutions")
    @PreAuthorize("@authz.canConfirmResult(#id)")
    public LineupsDto substitute(@PathVariable String id, @RequestBody LineupRequests.Substitute request) {
        return service.substitute(id, request);
    }

    @DeleteMapping("/{id}/substitutions/{eventId}")
    @PreAuthorize("@authz.canConfirmResult(#id)")
    public LineupsDto deleteSubstitution(@PathVariable String id, @PathVariable String eventId) {
        return service.deleteSubstitution(id, eventId);
    }
}
