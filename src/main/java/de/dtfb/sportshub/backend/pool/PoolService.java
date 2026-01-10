package de.dtfb.sportshub.backend.pool;

import de.dtfb.sportshub.backend.stage.Stage;
import de.dtfb.sportshub.backend.stage.StageNotFoundException;
import de.dtfb.sportshub.backend.stage.StageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    List<PoolDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    PoolDto get(String uuid) {
        Pool pool = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new PoolNotFoundException(uuid));
        return mapper.toDto(pool);
    }

    PoolDto create(PoolDto poolDto) {
        Pool pool = mapper.toEntity(poolDto);

        Stage stage = stageRepository.findById(poolDto.getStageId())
            .orElseThrow(() -> new StageNotFoundException(poolDto.getStageId().toString()));
        pool.setStage(stage);

        Pool savedPool = repository.save(pool);
        return mapper.toDto(savedPool);
    }

    PoolDto update(String uuid, PoolDto poolDto) {
        Pool pool = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new PoolNotFoundException(uuid));

        mapper.updateEntityFromDto(poolDto, pool);

        Stage stage = stageRepository.findById(poolDto.getStageId())
            .orElseThrow(() -> new StageNotFoundException(poolDto.getStageId().toString()));
        pool.setStage(stage);

        Pool savedPool = repository.save(pool);
        return mapper.toDto(savedPool);
    }

    @Transactional
    void delete(String uuid) {
        Pool pool = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new PoolNotFoundException(uuid));
        repository.delete(pool);
    }
}
