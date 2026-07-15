package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.tier.Tier;
import de.dtfb.sportshub.backend.tier.TierNotFoundException;
import de.dtfb.sportshub.backend.tier.TierRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {
    private final GroupRepository repository;
    private final GroupMapper mapper;
    private final TierRepository tierRepository;

    public GroupService(GroupRepository repository, GroupMapper mapper, TierRepository tierRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.tierRepository = tierRepository;
    }

    @Transactional(readOnly = true)
    public List<GroupDto> getAll() {
        return mapper.toDtoList(repository.findAllVisible());
    }

    @Transactional(readOnly = true)
    public GroupDto get(String id) {
        Group group = repository.findVisibleById(id).orElseThrow(
            () -> new GroupNotFoundException(id));
        return mapper.toDto(group);
    }

    @Transactional
    public GroupDto create(GroupDto groupDto) {
        Group group = mapper.toEntity(groupDto);

        Tier tier = tierRepository.findById(groupDto.getTierId())
            .orElseThrow(() -> new TierNotFoundException(groupDto.getTierId()));
        group.setTier(tier);

        Group savedGroup = repository.save(group);
        return mapper.toDto(savedGroup);
    }

    @Transactional
    public GroupDto update(String id, GroupDto groupDto) {
        Group group = repository.findById(id).orElseThrow(
            () -> new GroupNotFoundException(id));

        mapper.updateEntityFromDto(groupDto, group);

        Tier tier = tierRepository.findById(groupDto.getTierId())
            .orElseThrow(() -> new TierNotFoundException(groupDto.getTierId()));
        group.setTier(tier);

        Group savedGroup = repository.save(group);
        return mapper.toDto(savedGroup);
    }

    @Transactional
    public void delete(String id) {
        Group group = repository.findById(id).orElseThrow(
            () -> new GroupNotFoundException(id));
        repository.delete(group);
    }
}
