package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.pool.Pool;
import de.dtfb.sportshub.backend.pool.PoolNotFoundException;
import de.dtfb.sportshub.backend.pool.PoolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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

    List<RoundDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    RoundDto get(String uuid) {
        Round round = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));
        return mapper.toDto(round);
    }

    RoundDto create(RoundDto roundDto) {
        Round round = mapper.toEntity(roundDto);

        Pool pool = poolRepository.findById(roundDto.getPoolId())
            .orElseThrow(() -> new PoolNotFoundException(roundDto.getPoolId().toString()));
        round.setPool(pool);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    RoundDto update(String uuid, RoundDto roundDto) {
        Round round = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));

        mapper.updateEntityFromDto(roundDto, round);

        Pool pool = poolRepository.findById(roundDto.getPoolId())
            .orElseThrow(() -> new PoolNotFoundException(roundDto.getPoolId().toString()));
        round.setPool(pool);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    @Transactional
    void delete(String uuid) {
        Round round = repository.findById(UUID.fromString(uuid)).orElseThrow(
            () -> new RoundNotFoundException(uuid));
        repository.delete(round);
    }
}
