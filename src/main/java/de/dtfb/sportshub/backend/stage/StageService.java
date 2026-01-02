package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineNotFoundException;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StageService {
    private final StageRepository repository;
    private final StageMapper mapper;
    private final DisciplineRepository disciplineRepository;

    public StageService(StageRepository repository, StageMapper mapper, DisciplineRepository disciplineRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.disciplineRepository = disciplineRepository;
    }

    List<StageDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    StageDto get(String uuid) {
        Stage stage = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new StageNotFoundException(uuid));
        return mapper.toDto(stage);
    }

    StageDto create(StageDto stageDto) {
        Stage stage = mapper.toEntity(stageDto);
        stage.setUuid(UUID.randomUUID());

        Discipline discipline = disciplineRepository.findByUuid(stageDto.getDisciplineUuid())
            .orElseThrow(() -> new DisciplineNotFoundException(stageDto.getDisciplineUuid().toString()));
        stage.setDiscipline(discipline);

        Stage savedStage = repository.save(stage);
        return mapper.toDto(savedStage);
    }

    StageDto update(String uuid, StageDto stageDto) {
        Stage stage = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new StageNotFoundException(uuid));

        mapper.updateEntityFromDto(stageDto, stage);

        Discipline discipline = disciplineRepository.findByUuid(stageDto.getDisciplineUuid())
            .orElseThrow(() -> new DisciplineNotFoundException(stageDto.getDisciplineUuid().toString()));
        stage.setDiscipline(discipline);

        Stage savedStage = repository.save(stage);
        return mapper.toDto(savedStage);
    }

    @Transactional
    void delete(String uuid) {
        Stage stage = repository.findByUuid(UUID.fromString(uuid)).orElseThrow(
            () -> new StageNotFoundException(uuid));
        repository.delete(stage);
    }
}
