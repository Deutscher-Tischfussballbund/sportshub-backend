package de.dtfb.sportshub.backend.phase;

import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageNotFoundException;
import de.dtfb.sportshub.backend.stage.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PhaseService {
    private final PhaseRepository repository;
    private final PhaseMapper mapper;
    private final StageRepository stageRepository;

    public PhaseService(PhaseRepository repository, PhaseMapper mapper, StageRepository stageRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.stageRepository = stageRepository;
    }

    List<PhaseDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    PhaseDto get(String uuid) {
        Phase phase = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new PhaseNotFoundException(uuid));
        return mapper.toDto(phase);
    }

    PhaseDto create(PhaseDto phaseDto) {
        Phase phase = mapper.toEntity(phaseDto);
        phase.setUuid(UUID.randomUUID());

        Stage stage = stageRepository.findByUuid(phaseDto.getStageUuid())
            .orElseThrow(() -> new StageNotFoundException(phaseDto.getStageUuid().toString()));
        phase.setStage(stage);

        Phase savedPhase = repository.save(phase);
        return mapper.toDto(savedPhase);
    }

    PhaseDto update(String uuid, PhaseDto phaseDto) {
        Phase phase = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new PhaseNotFoundException(uuid));

        mapper.updateEntityFromDto(phaseDto, phase);

        Stage stage = stageRepository.findByUuid(phaseDto.getStageUuid())
            .orElseThrow(() -> new StageNotFoundException(phaseDto.getStageUuid().toString()));
        phase.setStage(stage);

        Phase savedPhase = repository.save(phase);
        return mapper.toDto(savedPhase);
    }

    @Transactional
    void delete(String uuid) {
        Phase phase = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new PhaseNotFoundException(uuid));
        repository.delete(phase);
    }
}
