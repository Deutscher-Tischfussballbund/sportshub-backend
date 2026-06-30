package de.dtfb.sportshub.backend.federation;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FederationMapper {

    FederationDto toDto(Federation federation);

    @Mapping(target = "id", ignore = true)
    Federation toEntity(FederationDto federationDto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(FederationDto dto, @MappingTarget Federation entity);

    List<FederationDto> toDtoList(List<Federation> federations);
}
