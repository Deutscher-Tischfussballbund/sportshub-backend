package de.dtfb.sportshub.backend.pool;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PoolMapper {

    @Mapping(source = "stage.uuid", target = "stageUuid")
    PoolDto toDto(Pool pool);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "stage", ignore = true)
    Pool toEntity(PoolDto poolDto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "uuid", ignore = true)
    @Mapping(target = "stage", ignore = true)
    void updateEntityFromDto(PoolDto dto, @MappingTarget Pool entity);

    List<PoolDto> toDtoList(List<Pool> pools);
}
