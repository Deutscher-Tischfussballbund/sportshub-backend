package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.importer.data.ImportTeam;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Component;

@Component
public class TeamImporter {
    private final TeamRepository teamRepository;

    public TeamImporter(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public Team importTeam(ImportTeam importingTeam) {
        return teamRepository
            .findByName(importingTeam.getName())
            .orElseGet(() -> {
                Team team = new Team();
                team.setName(importingTeam.getName());
                return teamRepository.save(team);
            });
    }
}
