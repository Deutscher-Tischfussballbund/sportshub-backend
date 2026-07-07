package de.dtfb.sportshub.backend.round;

import de.dtfb.sportshub.backend.group.Group;
import de.dtfb.sportshub.backend.group.GroupNotFoundException;
import de.dtfb.sportshub.backend.group.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoundService {
    private final RoundRepository repository;
    private final RoundMapper mapper;
    private final GroupRepository groupRepository;

    public RoundService(RoundRepository repository, RoundMapper mapper, GroupRepository groupRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.groupRepository = groupRepository;
    }

    @Transactional(readOnly = true)
    public List<RoundDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public RoundDto get(String id) {
        Round round = repository.findVisibleById(id).orElseThrow(
            () -> new RoundNotFoundException(id));
        return mapper.toDto(round);
    }

    @Transactional
    public RoundDto create(RoundDto roundDto) {
        Round round = mapper.toEntity(roundDto);

        Group group = groupRepository.findById(roundDto.getGroupId())
            .orElseThrow(() -> new GroupNotFoundException(roundDto.getGroupId()));
        round.setGroup(group);

        Round savedRound = repository.save(round);
        return mapper.toDto(savedRound);
    }

    @Transactional
    public RoundDto update(String id, RoundDto roundDto) {
        Round round = repository.findById(id).orElseThrow(
            () -> new RoundNotFoundException(id));

        mapper.updateEntityFromDto(roundDto, round);

        Group group = groupRepository.findById(roundDto.getGroupId())
            .orElseThrow(() -> new GroupNotFoundException(roundDto.getGroupId()));
        round.setGroup(group);

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
