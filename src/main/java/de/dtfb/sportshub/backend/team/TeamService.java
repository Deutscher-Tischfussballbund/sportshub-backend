package de.dtfb.sportshub.backend.team;

import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.federation.FederationNotFoundException;
import de.dtfb.sportshub.backend.federation.FederationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TeamService {
    private final TeamRepository repository;
    private final TeamMapper mapper;
    private final FederationRepository federationRepository;

    public TeamService(TeamRepository repository, TeamMapper mapper, FederationRepository federationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.federationRepository = federationRepository;
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public TeamDto get(String id) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));
        return mapper.toDto(team);
    }

    @Transactional
    public TeamDto create(TeamDto teamDto) {
        Team newTeam = mapper.toEntity(teamDto);
        resolveFederation(teamDto, newTeam);
        Team savedTeam = repository.save(newTeam);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    public TeamDto update(String id, TeamDto teamDto) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));

        mapper.updateEntityFromDto(teamDto, team);
        resolveFederation(teamDto, team);

        Team savedTeam = repository.save(team);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    public void delete(String id) {
        Team team = repository.findById(id).orElseThrow(
            () -> new TeamNotFoundException(id));
        repository.delete(team);
    }

    private void resolveFederation(TeamDto dto, Team team) {
        if (dto.getFederationId() != null) {
            Federation federation = federationRepository.findById(dto.getFederationId())
                .orElseThrow(() -> new FederationNotFoundException(dto.getFederationId()));
            team.setFederation(federation);
        }
    }
}
