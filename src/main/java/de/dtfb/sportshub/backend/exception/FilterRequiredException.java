package de.dtfb.sportshub.backend.exception;

import java.util.List;

/**
 * A large collection was requested without narrowing it down (SPO-120): instead of paging, these
 * endpoints need at least one of their scoping filters. Mapped to {@code 400} with code
 * {@code FILTER_REQUIRED}.
 */
public class FilterRequiredException extends RuntimeException {

    public FilterRequiredException(List<String> filters) {
        super("At least one of these filters is required: " + String.join(", ", filters));
    }

    /** Throws unless at least one of the values is set; {@code filters} names them in the same order. */
    public static void requireAny(List<String> filters, Object... values) {
        for (Object value : values) {
            if (value != null && !(value instanceof String text && text.isBlank())) {
                return;
            }
        }
        throw new FilterRequiredException(filters);
    }
}
