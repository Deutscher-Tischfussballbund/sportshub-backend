package de.dtfb.sportshub.backend.exception;

/** Standard error response body returned by {@link GlobalExceptionHandler}. */
public record ApiError(String code, String message) {
}
