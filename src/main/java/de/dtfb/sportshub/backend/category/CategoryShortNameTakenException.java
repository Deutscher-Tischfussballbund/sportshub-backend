package de.dtfb.sportshub.backend.category;

/**
 * Thrown when creating/updating a category would reuse another category's short name (compared
 * case-insensitively). Mapped to {@code 409 Conflict} with code {@code CATEGORY_SHORT_NAME_TAKEN}.
 */
public class CategoryShortNameTakenException extends RuntimeException {

    public CategoryShortNameTakenException(String shortName) {
        super("Another category already uses the short name '" + shortName + "'");
    }
}
