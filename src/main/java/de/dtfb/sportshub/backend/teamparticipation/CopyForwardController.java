package de.dtfb.sportshub.backend.teamparticipation;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Copy-forward endpoint (L1b): seed a season's placements from a previous season. Gated on the
 * target season's region — a region admin carries their own league forward. The same-federation
 * rule (enforced in the service) keeps this within one region.
 */
@RestController
@RequestMapping("/v1/seasons")
public class CopyForwardController {

    private final CopyForwardService service;

    public CopyForwardController(CopyForwardService service) {
        this.service = service;
    }

    @PostMapping("/{targetSeasonId}/copy-forward")
    @PreAuthorize("@authz.canManageSeason(#targetSeasonId)")
    public CopyForwardResultDto copyForward(@PathVariable String targetSeasonId, @RequestParam String from) {
        return service.copyForward(targetSeasonId, from);
    }
}
