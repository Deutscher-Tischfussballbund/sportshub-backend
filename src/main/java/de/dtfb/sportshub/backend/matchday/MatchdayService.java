package de.dtfb.sportshub.backend.matchday;

import de.dtfb.sportshub.backend.location.Location;
import de.dtfb.sportshub.backend.location.LocationNotFoundException;
import de.dtfb.sportshub.backend.location.LocationRepository;
import de.dtfb.sportshub.backend.round.Round;
import de.dtfb.sportshub.backend.round.RoundNotFoundException;
import de.dtfb.sportshub.backend.round.RoundRepository;
import de.dtfb.sportshub.backend.team.Team;
import de.dtfb.sportshub.backend.team.TeamNotFoundException;
import de.dtfb.sportshub.backend.team.TeamRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class MatchdayService {
    private final MatchdayRepository repository;
    private final MatchdayMapper mapper;
    private final RoundRepository roundRepository;
    private final LocationRepository locationRepository;
    private final TeamRepository teamRepository;

    public MatchdayService(MatchdayRepository repository, MatchdayMapper mapper, RoundRepository roundRepository, LocationRepository locationRepository, TeamRepository teamRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.roundRepository = roundRepository;
        this.locationRepository = locationRepository;
        this.teamRepository = teamRepository;
    }

    List<MatchdayDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchdayDto get(String uuid) {
        Matchday matchday = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchdayNotFoundException(uuid));
        return mapper.toDto(matchday);
    }

    MatchdayDto create(MatchdayDto matchdayDto) {
        Matchday matchday = mapper.toEntity(matchdayDto);
        matchday.setUuid(UUID.randomUUID());

        setDependants(matchdayDto, matchday);

        Matchday savedMatchday = repository.save(matchday);
        return mapper.toDto(savedMatchday);
    }

    MatchdayDto update(String uuid, MatchdayDto matchdayDto) {
        Matchday matchday = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchdayNotFoundException(uuid));

        mapper.updateEntityFromDto(matchdayDto, matchday);

        setDependants(matchdayDto, matchday);

        Matchday savedMatchday = repository.save(matchday);
        return mapper.toDto(savedMatchday);
    }

    @Transactional
    void delete(String uuid) {
        Matchday matchday = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchdayNotFoundException(uuid));
        repository.delete(matchday);
    }

    private void setDependants(MatchdayDto matchdayDto, Matchday matchday) {
        Round round = roundRepository.findByUuid(matchdayDto.getRoundUuid())
            .orElseThrow(() -> new RoundNotFoundException(matchdayDto.getRoundUuid().toString()));
        matchday.setRound(round);
        Location location = locationRepository.findByUuid(matchdayDto.getLocationUuid())
            .orElseThrow(() -> new LocationNotFoundException(matchdayDto.getLocationUuid().toString()));
        matchday.setLocation(location);
        Team teamAway = teamRepository.findByUuid(matchdayDto.getTeamAwayUuid())
            .orElseThrow(() -> new TeamNotFoundException(matchdayDto.getTeamAwayUuid().toString()));
        matchday.setTeamAway(teamAway);
        Team teamHome = teamRepository.findByUuid(matchdayDto.getTeamHomeUuid())
            .orElseThrow(() -> new TeamNotFoundException(matchdayDto.getTeamHomeUuid().toString()));
        matchday.setTeamHome(teamHome);
    }
}
