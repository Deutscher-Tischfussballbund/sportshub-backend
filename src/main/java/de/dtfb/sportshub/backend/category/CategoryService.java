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

    @Transactional(readOnly = true)
    public List<CategoryDto> getAll() {
        return mapper.toDtoList(repository.findAll());
    }

    @Transactional(readOnly = true)
    public CategoryDto get(String id) {
        return mapper.toDto(getCategory(id));
    }

    @Transactional
    public CategoryDto create(CategoryDto categoryDto) {
        Category category = mapper.toEntity(categoryDto);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public CategoryDto update(String id, CategoryDto categoryDto) {
        Category category = getCategory(id);

        mapper.updateEntityFromDto(categoryDto, category);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getCategory(id));
    }

    private @NonNull Category getCategory(String id) {
        return repository.findById(id).orElseThrow(
            () -> new CategoryNotFoundException(id));
    }
}
