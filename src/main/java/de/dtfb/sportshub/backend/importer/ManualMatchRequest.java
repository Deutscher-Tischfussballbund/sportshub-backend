package de.dtfb.sportshub.backend.importer;

/** Assigns a player record to an existing player; {@code playerId} null removes the assignment. */
public record ManualMatchRequest(String playerId) {
}
