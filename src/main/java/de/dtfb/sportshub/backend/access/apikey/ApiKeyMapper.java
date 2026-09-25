package de.dtfb.sportshub.backend.access.apikey;

import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ApiKeyMapper {

    ApiKeyDto toDto(ApiKey apiKey);

    List<ApiKeyDto> toDtoList(List<ApiKey> apiKeys);
}
