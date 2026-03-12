package de.dtfb.sportshub.backend.federation;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface FederationMapper {

    @Mapping(source = "season.id", target = "seasonId")
    FederationDto toDto(Federation event);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    Federation toEntity(FederationDto eventDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "season", ignore = true)
    void updateEntityFromDto(FederationDto dto, @MappingTarget Federation entity);

    List<FederationDto> toDtoList(List<Federation> events);
}
