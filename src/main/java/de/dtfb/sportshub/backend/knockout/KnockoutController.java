package de.dtfb.sportshub.backend.knockout;

import de.dtfb.sportshub.backend.matchday.MatchDayDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Knock-out stages (docs/34): sources, places and team overrides of their fixtures. */
@RestController
public class KnockoutController {

    private final KnockoutService service;
    private final KnockoutStageService stages;
    private final FinalRankingService ranking;

    public KnockoutController(KnockoutService service, KnockoutStageService stages, FinalRankingService ranking) {
        this.service = service;
        this.stages = stages;
        this.ranking = ranking;
    }

    /** The tier's final ranking: knock-out places, shared ranges, group places; promotion/relegation marks. */
    @GetMapping("/v1/tiers/{id}/final-ranking")
    public List<FinalRankingRowDto> getFinalRanking(@PathVariable String id) {
        return ranking.get(id);
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
