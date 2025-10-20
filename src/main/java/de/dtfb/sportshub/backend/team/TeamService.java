package de.dtfb.sportshub.backend.team;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {
    private final TeamRepository teamRepository;

    public TeamService(TeamRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    public List<Team> getAllTeams(){
        return teamRepository.findAll();
    }

    public Team getTeamById(Long id) {
        return teamRepository.findById(id).orElse(null);
    }

    public Team createTeam(Team team) {
        return teamRepository.save(team);
    }
}
