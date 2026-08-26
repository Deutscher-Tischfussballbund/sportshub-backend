package de.dtfb.sportshub.backend.access.apiclient;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ApiClientGrantMapper {

    ApiClientGrantDto toDto(ApiClientGrant grant);

    // id/createdAt/lastUsedAt are server-controlled -- ignored here so a client-supplied value in
    // the DTO can never override them (mirrors ReleaseNoteMapper's publishedAt handling).
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastUsedAt", ignore = true)
    ApiClientGrant toEntity(ApiClientGrantDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "lastUsedAt", ignore = true)
    void updateEntityFromDto(ApiClientGrantDto dto, @MappingTarget ApiClientGrant entity);

    List<ApiClientGrantDto> toDtoList(List<ApiClientGrant> grants);
}
