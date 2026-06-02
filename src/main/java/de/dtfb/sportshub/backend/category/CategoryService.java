package de.dtfb.sportshub.backend.category;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {
    private final CategoryRepository repository;
    private final CategoryMapper mapper;

    public CategoryService(CategoryRepository repository, CategoryMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    List<CategoryDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    CategoryDto get(String uuid) {
        return mapper.toDto(getCategory(uuid));
    }

    @Transactional
    CategoryDto create(CategoryDto categoryDto) {
        Category category = mapper.toEntity(categoryDto);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    CategoryDto update(String uuid, CategoryDto categoryDto) {
        Category category = getCategory(uuid);

        mapper.updateEntityFromDto(categoryDto, category);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    void delete(String uuid) {
        repository.delete(getCategory(uuid));
    }

    private @NonNull Category getCategory(String uuid) {
        return repository.findById(uuid).orElseThrow(
            () -> new CategoryNotFoundException(uuid));
    }
}
