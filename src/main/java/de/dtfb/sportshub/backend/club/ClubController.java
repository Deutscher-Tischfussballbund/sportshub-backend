package de.dtfb.sportshub.backend.club;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ClubController {

    private final ClubService service;

    public ClubController(ClubService service) {
        this.service = service;
    }

    @GetMapping("/v1/admin/clubs")
    public List<ClubDto> clubs() {
        return service.getAll();
    }
}
