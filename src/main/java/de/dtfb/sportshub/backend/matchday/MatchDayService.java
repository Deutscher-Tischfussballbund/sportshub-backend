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
public class MatchDayService {
    private final MatchDayRepository repository;
    private final MatchDayMapper mapper;
    private final RoundRepository roundRepository;
    private final LocationRepository locationRepository;
    private final TeamRepository teamRepository;

    public MatchDayService(MatchDayRepository repository, MatchDayMapper mapper, RoundRepository roundRepository, LocationRepository locationRepository, TeamRepository teamRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.roundRepository = roundRepository;
        this.locationRepository = locationRepository;
        this.teamRepository = teamRepository;
    }

    List<MatchDayDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    MatchDayDto get(String uuid) {
        MatchDay matchDay = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchDayNotFoundException(uuid));
        return mapper.toDto(matchDay);
    }

    MatchDayDto create(MatchDayDto matchDayDto) {
        MatchDay matchDay = mapper.toEntity(matchDayDto);
        matchDay.setUuid(UUID.randomUUID());

        setDependants(matchDayDto, matchDay);

        MatchDay savedMatchDay = repository.save(matchDay);
        return mapper.toDto(savedMatchDay);
    }

    MatchDayDto update(String uuid, MatchDayDto matchDayDto) {
        MatchDay matchDay = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchDayNotFoundException(uuid));

        mapper.updateEntityFromDto(matchDayDto, matchDay);

        setDependants(matchDayDto, matchDay);

        MatchDay savedMatchDay = repository.save(matchDay);
        return mapper.toDto(savedMatchDay);
    }

    @Transactional
    void delete(String uuid) {
        MatchDay matchDay = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new MatchDayNotFoundException(uuid));
        repository.delete(matchDay);
    }

    private void setDependants(MatchDayDto matchDayDto, MatchDay matchDay) {
        Round round = roundRepository.findByUuid(matchDayDto.getRoundUuid())
            .orElseThrow(() -> new RoundNotFoundException(matchDayDto.getRoundUuid().toString()));
        matchDay.setRound(round);
        Location location = locationRepository.findByUuid(matchDayDto.getLocationUuid())
            .orElseThrow(() -> new LocationNotFoundException(matchDayDto.getLocationUuid().toString()));
        matchDay.setLocation(location);
        Team teamAway = teamRepository.findByUuid(matchDayDto.getTeamAwayUuid())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamAwayUuid().toString()));
        matchDay.setTeamAway(teamAway);
        Team teamHome = teamRepository.findByUuid(matchDayDto.getTeamHomeUuid())
            .orElseThrow(() -> new TeamNotFoundException(matchDayDto.getTeamHomeUuid().toString()));
        matchDay.setTeamHome(teamHome);
    }
}
