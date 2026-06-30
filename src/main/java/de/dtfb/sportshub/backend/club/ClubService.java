package de.dtfb.sportshub.backend.club;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ClubService {

    private final ClubRepository repository;
    private final ClubMapper mapper;

    public ClubService(ClubRepository repository, ClubMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<ClubDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }
}
