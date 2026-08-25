package de.dtfb.sportshub.backend.access.roleassignment;

import de.dtfb.sportshub.backend.user.UserMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Structure mapping for {@link RoleAssignment}. The cross-entity values the assignment only
 * references by id — the granter (id/name) and the scope name — are resolved in
 * {@link RoleAdminService} (batch-loaded) and passed in, since a mapper cannot query.
 */
@Mapper(componentModel = "spring", uses = UserMapper.class)
public interface RoleAssignmentMapper {

    @Mapping(target = "userId", source = "ra.user.id")
    @Mapping(target = "grantedById", source = "grantedById")
    @Mapping(target = "createdAt", expression = "java(ra.getCreatedAt().toString())")
    RoleAssignmentDto toDto(RoleAssignment ra, String grantedById);

    @Mapping(target = "user", source = "ra.user")
    @Mapping(target = "scopeName", source = "scopeName")
    @Mapping(target = "grantedByName", source = "grantedByName")
    @Mapping(target = "createdAt", expression = "java(ra.getCreatedAt().toString())")
    RoleAssignmentViewDto toViewDto(RoleAssignment ra, String scopeName, String grantedByName);
}
