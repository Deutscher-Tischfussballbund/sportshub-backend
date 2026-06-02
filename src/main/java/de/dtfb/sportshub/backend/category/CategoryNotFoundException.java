package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.exception.NotFoundExceptionMarker;

public class CategoryNotFoundException extends NotFoundExceptionMarker {
    public final String errorCode = "CATEGORY_NOT_FOUND";

    public CategoryNotFoundException(String uuid) {
        super("Could not find category with uuid " + uuid);
    }

    @Override
    public String getErrorCode() {
        return errorCode;
    }
}
