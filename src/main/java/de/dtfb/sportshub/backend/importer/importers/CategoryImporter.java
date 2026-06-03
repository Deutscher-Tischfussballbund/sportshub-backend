package de.dtfb.sportshub.backend.importer.importers;

import de.dtfb.sportshub.backend.category.Category;
import de.dtfb.sportshub.backend.category.CategoryRepository;
import de.dtfb.sportshub.backend.importer.data.ImportDiscipline;
import org.springframework.stereotype.Component;

@Component
public class CategoryImporter {
    private final CategoryRepository categoryRepository;

    public CategoryImporter(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Category importCategory(ImportDiscipline importingDiscipline) {
        return categoryRepository
            .findByNameAndShortName(importingDiscipline.getName(), importingDiscipline.getShortName())
            .orElseGet(() -> {
                Category category = new Category();
                category.setName(importingDiscipline.getName());
                category.setShortName(importingDiscipline.getShortName());
                return categoryRepository.save(category);
            });
    }
}
