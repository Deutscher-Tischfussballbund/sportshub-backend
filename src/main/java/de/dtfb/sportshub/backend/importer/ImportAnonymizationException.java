package de.dtfb.sportshub.backend.importer;

import lombok.Getter;

/**
 * The export's pseudonymization doesn't fit this instance ({@link AnonymizationPolicy}): a real export
 * on a test system, or a pseudonymized one in production. Mapped to 400 with {@link #getCode()}.
 */
@Getter
public class ImportAnonymizationException extends RuntimeException {

    private final String code;

    public ImportAnonymizationException(String code, String message) {
        super(message);
        this.code = code;
    }
}
