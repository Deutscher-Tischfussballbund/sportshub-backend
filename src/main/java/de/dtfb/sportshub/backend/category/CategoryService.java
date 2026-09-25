package de.dtfb.sportshub.backend.category;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        normalize(categoryDto);
        if (repository.existsByShortNameIgnoreCase(categoryDto.getShortName())) {
            throw new CategoryShortNameTakenException(categoryDto.getShortName());
        }
        Category category = mapper.toEntity(categoryDto);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public CategoryDto update(String id, CategoryDto categoryDto) {
        Category category = getCategory(id);
        normalize(categoryDto);
        if (repository.existsByShortNameIgnoreCaseAndIdNot(categoryDto.getShortName(), id)) {
            throw new CategoryShortNameTakenException(categoryDto.getShortName());
        }

        mapper.updateEntityFromDto(categoryDto, category);

        return mapper.toDto(repository.save(category));
    }

    @Transactional
    public void delete(String id) {
        repository.delete(getCategory(id));
    }

    /** Name and short name are mandatory; both are trimmed before the uniqueness check and save. */
    private static void normalize(CategoryDto dto) {
        String name = dto.getName() == null ? "" : dto.getName().trim();
        String shortName = dto.getShortName() == null ? "" : dto.getShortName().trim();
        if (name.isEmpty() || shortName.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name and shortName are required");
        }
        dto.setName(name);
        dto.setShortName(shortName);
    }

    private @NonNull Category getCategory(String id) {
        return repository.findById(id).orElseThrow(
            () -> new CategoryNotFoundException(id));
    }
}
