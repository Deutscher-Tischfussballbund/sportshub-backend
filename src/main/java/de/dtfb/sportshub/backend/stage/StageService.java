package de.dtfb.sportshub.backend.stage;

import de.dtfb.sportshub.backend.discipline.Discipline;
import de.dtfb.sportshub.backend.discipline.DisciplineNotFoundException;
import de.dtfb.sportshub.backend.discipline.DisciplineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<StageDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public StageDto get(String id) {
        Stage stage = repository.findVisibleById(id).orElseThrow(
            () -> new StageNotFoundException(id));
        return mapper.toDto(stage);
    }

    @Transactional
    public StageDto create(StageDto stageDto) {
        Stage stage = mapper.toEntity(stageDto);

        Discipline discipline = disciplineRepository.findById(stageDto.getDisciplineId())
            .orElseThrow(() -> new DisciplineNotFoundException(stageDto.getDisciplineId()));
        stage.setDiscipline(discipline);

        Stage savedStage = repository.save(stage);
        return mapper.toDto(savedStage);
    }

    @Transactional
    public StageDto update(String id, StageDto stageDto) {
        Stage stage = repository.findById(id).orElseThrow(
            () -> new StageNotFoundException(id));

        mapper.updateEntityFromDto(stageDto, stage);

        Discipline discipline = disciplineRepository.findById(stageDto.getDisciplineId())
            .orElseThrow(() -> new DisciplineNotFoundException(stageDto.getDisciplineId()));
        stage.setDiscipline(discipline);

        Stage savedStage = repository.save(stage);
        return mapper.toDto(savedStage);
    }

    @Transactional
    public void delete(String id) {
        Stage stage = repository.findById(id).orElseThrow(
            () -> new StageNotFoundException(id));
        repository.delete(stage);
    }
}
