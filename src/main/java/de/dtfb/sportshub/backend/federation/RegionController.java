package de.dtfb.sportshub.backend.federation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The admin frontend's {@code /v1/regions} read. A "region" is a {@link Federation}
 * (Landesverband); this reuses {@link FederationService} so there is one source of truth.
 */
@RestController
public class RegionController {

    private final FederationService service;

    public RegionController(FederationService service) {
        this.service = service;
    }

    @GetMapping("/v1/regions")
    public List<FederationDto> regions() {
        return service.getAll();
    }
}
