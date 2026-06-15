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

    List<TeamDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    TeamDto get(String uuid) {
        Team team = repository.findById(uuid).orElseThrow(
            () -> new TeamNotFoundException(uuid));
        return mapper.toDto(team);
    }

    TeamDto create(TeamDto teamDto) {
        Team newTeam = mapper.toEntity(teamDto);
        resolveFederation(teamDto, newTeam);
        Team savedTeam = repository.save(newTeam);
        return mapper.toDto(savedTeam);
    }

    TeamDto update(String uuid, TeamDto teamDto) {
        Team team = repository.findById(uuid).orElseThrow(
            () -> new TeamNotFoundException(uuid));

        mapper.updateEntityFromDto(teamDto, team);
        resolveFederation(teamDto, team);

        Team savedTeam = repository.save(team);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    void delete(String uuid) {
        Team team = repository.findById(uuid).orElseThrow(
            () -> new TeamNotFoundException(uuid));
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
