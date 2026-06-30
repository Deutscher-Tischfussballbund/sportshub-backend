package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class CategoryNotFoundException extends NotFoundExceptionMarker {
    public CategoryNotFoundException(String id) {
        super("category", "CATEGORY_NOT_FOUND", id);
    }
}
