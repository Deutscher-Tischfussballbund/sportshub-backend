package de.dtfb.sportshub.backend.exception;

import de.dtfb.sportshub.backend.externalApi.ApiError;
import de.dtfb.sportshub.backend.externalApi.ExternalApiException;
import de.dtfb.sportshub.backend.externalApi.ExternalApiUnavailableException;
import de.dtfb.sportshub.backend.externalApi.ExternalResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundExceptionMarker.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleNotFound(NotFoundExceptionMarker ex, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", 404);
        body.put("error", "Not Found");
        body.put("message", ex.getMessage());
        body.put("errorCode", ex.getErrorCode());
        body.put("path", request.getRequestURI());
        return body;
    }

    @ExceptionHandler(ExternalApiUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError handleExternalUnavailable() {
        return new ApiError("SERVICE_UNAVAILABLE",
            "Required external api is currently unavailable");
    }

    @ExceptionHandler(ExternalResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleExternalNotFound() {
        return new ApiError("NOT_FOUND",
            "Request to external api failed, resource not found");
    }

    @ExceptionHandler(ExternalApiException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleExternalBadRequest() {
        return new ApiError("BAD_REQUEST",
            "Request to external api is faulty");
    }

    // Failsafe
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiError handleUnexpected() {
        return new ApiError(
            "INTERNAL_ERROR",
            "An unexpected error occurred");
    }
}
