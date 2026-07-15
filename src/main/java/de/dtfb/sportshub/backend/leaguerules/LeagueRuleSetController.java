package de.dtfb.sportshub.backend.leaguerules;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/v1/league-rule-sets")
public class LeagueRuleSetController {

    private final LeagueRuleSetService service;

    public LeagueRuleSetController(LeagueRuleSetService service) {
        this.service = service;
    }

    @GetMapping
    public List<LeagueRuleSetDto> getAllLeagueRuleSets() {
        return service.getAll();
    }

    @PostMapping
    @PreAuthorize("@authz.canManageRuleSet(#ruleSetDto.federationId)")
    public ResponseEntity<LeagueRuleSetDto> createLeagueRuleSet(@RequestBody LeagueRuleSetDto ruleSetDto) {
        LeagueRuleSetDto returnedDto = service.create(ruleSetDto);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/" + returnedDto.getId()).build().toUri();

        return ResponseEntity.created(location).body(returnedDto);
    }

    @GetMapping("/{id}")
    public LeagueRuleSetDto getLeagueRuleSet(@PathVariable String id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authz.canManageRuleSetById(#id)")
    public LeagueRuleSetDto updateLeagueRuleSet(@PathVariable String id, @RequestBody LeagueRuleSetDto ruleSetDto) {
        return service.update(id, ruleSetDto);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authz.canManageRuleSetById(#id)")
    public void deleteLeagueRuleSet(@PathVariable String id) {
        service.delete(id);
    }
}
