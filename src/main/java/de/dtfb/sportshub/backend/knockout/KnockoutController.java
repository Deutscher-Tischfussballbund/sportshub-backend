package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.matchday.MatchDayDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Knock-out stages (docs/34): sources, places and team overrides of their fixtures. */
@RestController
public class KnockoutController {

    private final KnockoutService service;
    private final KnockoutStageService stages;

    public KnockoutController(KnockoutService service, KnockoutStageService stages) {
        this.service = service;
        this.stages = stages;
    }

    @PostMapping("/v1/tiers/{id}/knockout-stage")
    @PreAuthorize("@authz.canOrganizeTier(#id)")
    public KnockoutStageDto createKnockoutStage(@PathVariable String id, @RequestBody KnockoutStageRequest request) {
        return stages.create(id, request);
    }

    @PutMapping("/v1/matchdays/{id}/knockout")
    @PreAuthorize("@authz.canOrganizeMatchDay(#id)")
    public MatchDayDto updateKnockoutFixture(@PathVariable String id, @RequestBody KnockoutFixtureRequest request) {
        return service.update(id, request);
    }
}
