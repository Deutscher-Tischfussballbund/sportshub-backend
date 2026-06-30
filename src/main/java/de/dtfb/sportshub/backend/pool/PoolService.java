package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageNotFoundException;
import de.dtfb.sportshub.backend.stage.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PoolService {
    private final PoolRepository repository;
    private final PoolMapper mapper;
    private final StageRepository stageRepository;

    public PoolService(PoolRepository repository, PoolMapper mapper, StageRepository stageRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.stageRepository = stageRepository;
    }

    @Transactional(readOnly = true)
    public List<PoolDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public PoolDto get(String id) {
        Pool pool = repository.findById(id).orElseThrow(
            () -> new PoolNotFoundException(id));
        return mapper.toDto(pool);
    }

    @Transactional
    public PoolDto create(PoolDto poolDto) {
        Pool pool = mapper.toEntity(poolDto);

        Stage stage = stageRepository.findById(poolDto.getStageId())
            .orElseThrow(() -> new StageNotFoundException(poolDto.getStageId()));
        pool.setStage(stage);

        Pool savedPool = repository.save(pool);
        return mapper.toDto(savedPool);
    }

    @Transactional
    public PoolDto update(String id, PoolDto poolDto) {
        Pool pool = repository.findById(id).orElseThrow(
            () -> new PoolNotFoundException(id));

        mapper.updateEntityFromDto(poolDto, pool);

        Stage stage = stageRepository.findById(poolDto.getStageId())
            .orElseThrow(() -> new StageNotFoundException(poolDto.getStageId()));
        pool.setStage(stage);

        Pool savedPool = repository.save(pool);
        return mapper.toDto(savedPool);
    }

    @Transactional
    public void delete(String id) {
        Pool pool = repository.findById(id).orElseThrow(
            () -> new PoolNotFoundException(id));
        repository.delete(pool);
    }
}
