package de.dtfb.sportshub.backend.location;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface LocationMapper {

    LocationDto toDto(Location season);

    @Mapping(target = "id", ignore = true)
    Location toEntity(LocationDto seasonDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    void updateEntityFromDto(LocationDto dto, @MappingTarget Location entity);

    List<LocationDto> toDtoList(List<Location> seasons);
}
