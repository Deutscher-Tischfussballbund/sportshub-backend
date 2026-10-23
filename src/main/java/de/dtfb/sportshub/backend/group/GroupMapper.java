package de.dtfb.sportshub.backend.group;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface GroupMapper {

    @Mapping(source = "tier.id", target = "tierId")
    @Mapping(source = "ruleSet.id", target = "ruleSetId")
    @Mapping(source = "ruleSet.name", target = "ruleSetName")
    @Mapping(source = "ruleSet.sourceBlueprint.id", target = "blueprintId")
    GroupDto toDto(Group group);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tier", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    @Mapping(target = "kind", ignore = true)
    Group toEntity(GroupDto groupDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "tier", ignore = true)
    @Mapping(target = "ruleSet", ignore = true)
    @Mapping(target = "kind", ignore = true)
    void updateEntityFromDto(GroupDto dto, @MappingTarget Group entity);

    List<GroupDto> toDtoList(List<Group> groups);
}
