package de.dtfb.sportshub.backend.group;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(source = "tier.id", target = "tierId")
    GroupDto toDto(Group group);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tier", ignore = true)
    Group toEntity(GroupDto groupDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tier", ignore = true)
    void updateEntityFromDto(GroupDto dto, @MappingTarget Group entity);

    List<GroupDto> toDtoList(List<Group> groups);
}
