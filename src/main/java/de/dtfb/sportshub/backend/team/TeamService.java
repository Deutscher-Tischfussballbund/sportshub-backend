package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.team.membership.TeamMembership;
import de.dtfb.sportshub.backend.team.membership.TeamMembershipEnum;
import de.dtfb.sportshub.backend.team.membership.TeamMembershipRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TeamService {
    private final TeamRepository teamRepository;
    private final TeamMembershipRepository teamMembershipRepository;
    private final TeamMapper teamMapper;

    public TeamService(TeamRepository teamRepository, TeamMembershipRepository teamMembershipRepository, TeamMapper teamMapper) {
        this.teamRepository = teamRepository;
        this.teamMembershipRepository = teamMembershipRepository;
        this.teamMapper = teamMapper;
    }

    public List<TeamDto> getAllTeams() {
        return teamMapper.toDtoList(teamRepository.findAll());
    }

    public TeamDto getTeamById(Long id) {
        return teamMapper.toDto(teamRepository.findById(id).orElse(null));
    }

    public TeamDto createTeam(TeamDto teamDto) {
        Team entity = teamMapper.toEntity(teamDto);
        Team saved = teamRepository.save(entity);
        return teamMapper.toDto(saved);
    }

    public TeamDto updateTeam(TeamDto teamDto) {
        Team existingTeam = teamRepository.findByUuid(teamDto.getId()).orElseThrow();
        teamMapper.updateEntityFromDto(teamDto, existingTeam);
        Team saved = teamRepository.save(existingTeam);
        return teamMapper.toDto(saved);
    }

    public TeamMembership getMembership(Long teamId, TeamMembershipEnum role) {
        return teamMembershipRepository.findByTeamIdAndRole(teamId, role).orElse(null);
    }
}
