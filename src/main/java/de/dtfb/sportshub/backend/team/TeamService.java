package de.dtfb.sportshub.backend.team;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TeamService {
    private final TeamRepository repository;
    private final TeamMapper mapper;

    public TeamService(TeamRepository repository, TeamMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    List<TeamDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    TeamDto get(String uuid) {
        Team team = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new TeamNotFoundException(uuid));
        return mapper.toDto(team);
    }

    TeamDto create(TeamDto teamDto) {
        Team newTeam = mapper.toEntity(teamDto);
        Team savedTeam = repository.save(newTeam);
        return mapper.toDto(savedTeam);
    }

    TeamDto update(String uuid, TeamDto teamDto) {
        Team team = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new TeamNotFoundException(uuid));

        mapper.updateEntityFromDto(teamDto, team);

        Team savedTeam = repository.save(team);
        return mapper.toDto(savedTeam);
    }

    @Transactional
    void delete(String uuid) {
        Team team = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new TeamNotFoundException(uuid));
        repository.delete(team);
    }
}
