package de.dtfb.sportshub.backend.group;

import de.dtfb.sportshub.backend.teamparticipation.TeamParticipationRepository;
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
    private final TeamParticipationRepository teamParticipationRepository;

    public GroupService(GroupRepository repository, GroupMapper mapper, TierRepository tierRepository,
                        TeamParticipationRepository teamParticipationRepository) {
        this.repository = repository;
        this.mapper = mapper;
        this.tierRepository = tierRepository;
        this.teamParticipationRepository = teamParticipationRepository;
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

    /** A group blocks its own delete while a team is still placed in it. */
    @Transactional
    public void delete(String id) {
        Group group = repository.findById(id).orElseThrow(
            () -> new GroupNotFoundException(id));
        if (teamParticipationRepository.existsByGroup_Id(id)) {
            throw new GroupDeletionBlockedException(
                "Group has team participations placed in it; move or unplace them before deleting the group");
        }
        repository.delete(group);
    }
}
