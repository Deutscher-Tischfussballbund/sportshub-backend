package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolNotFoundException;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoundService {
    private final RoundRepository repository;
    private final RoundMapper mapper;
    private final PoolRepository poolRepository;

    public RoundService(RoundRepository repository, RoundMapper mapper, PoolRepository poolRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.poolRepository = poolRepository;
    }

    @Transactional(readOnly = true)
    public List<RoundDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public RoundDto get(String id) {
        Round round = repository.findById(id).orElseThrow(
            () -> new RoundNotFoundException(id));
        return mapper.toDto(round);
    }

    @Transactional
    public RoundDto create(RoundDto roundDto) {
        Round round = mapper.toEntity(roundDto);

        Pool pool = poolRepository.findById(roundDto.getPoolId())
            .orElseThrow(() -> new PoolNotFoundException(roundDto.getPoolId()));
        round.setPool(pool);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    @Transactional
    public RoundDto update(String id, RoundDto roundDto) {
        Round round = repository.findById(id).orElseThrow(
            () -> new RoundNotFoundException(id));

        mapper.updateEntityFromDto(roundDto, round);

        Pool pool = poolRepository.findById(roundDto.getPoolId())
            .orElseThrow(() -> new PoolNotFoundException(roundDto.getPoolId()));
        round.setPool(pool);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    @Transactional
    public void delete(String id) {
        Round round = repository.findById(id).orElseThrow(
            () -> new RoundNotFoundException(id));
        repository.delete(round);
    }
}
