package de.dtfb.sportshub.backend.category;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CategoryMapper {

    CategoryDto toDto(Category category);

    @Mapping(target = "id", ignore = true)
    Category toEntity(CategoryDto categoryDto);

    @Mapping(target = "id", ignore = true)
    void updateEntityFromDto(CategoryDto dto, @MappingTarget Category entity);

    List<CategoryDto> toDtoList(List<Category> categories);
}
