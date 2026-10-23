package de.dtfb.sportshub.backend.knockout;

/**
 * Thrown when an admin corrects a final result whose outcome already fed a later knock-out fixture that
 * has a result (docs/34) -- correct the later one first. Mapped to {@code 409 Conflict}
 * ({@code KNOCKOUT_DOWNSTREAM_PLAYED}).
 */
public class KnockoutDownstreamPlayedException extends RuntimeException {

    public KnockoutDownstreamPlayedException(String message) {
        super(message);
    }
}
