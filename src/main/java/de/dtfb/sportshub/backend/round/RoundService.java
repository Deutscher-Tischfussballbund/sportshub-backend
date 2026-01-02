package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.phase.Phase;
import de.dtfb.sportshub.backend.phase.PhaseNotFoundException;
import de.dtfb.sportshub.backend.phase.PhaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RoundService {
    private final RoundRepository repository;
    private final RoundMapper mapper;
    private final PhaseRepository phaseRepository;

    public RoundService(RoundRepository repository, RoundMapper mapper, PhaseRepository phaseRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.phaseRepository = phaseRepository;
    }

    List<RoundDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    RoundDto get(String uuid) {
        Round round = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));
        return mapper.toDto(round);
    }

    RoundDto create(RoundDto roundDto) {
        Round round = mapper.toEntity(roundDto);
        round.setUuid(UUID.randomUUID());

        Phase phase = phaseRepository.findByUuid(roundDto.getPhaseUuid())
            .orElseThrow(() -> new PhaseNotFoundException(roundDto.getPhaseUuid().toString()));
        round.setPhase(phase);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    RoundDto update(String uuid, RoundDto roundDto) {
        Round round = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));

        mapper.updateEntityFromDto(roundDto, round);

        Phase phase = phaseRepository.findByUuid(roundDto.getPhaseUuid())
            .orElseThrow(() -> new PhaseNotFoundException(roundDto.getPhaseUuid().toString()));
        round.setPhase(phase);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    @Transactional
    void delete(String uuid) {
        Round round = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));
        repository.delete(round);
    }
}
