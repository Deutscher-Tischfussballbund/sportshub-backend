package de.dtfb.sportshub.backend.federation;

/**
 * Thrown when {@code Federation.defaultRuleSetId} is being changed while a tier somewhere in the
 * federation already has fixtures (a {@code Round}) and no explicit rule set of its own (neither the
 * tier nor its league) — changing the default would silently alter what that tier's next matchday
 * confirmation bakes into standings. Mapped to {@code 409 Conflict} — give the affected tier its own
 * explicit rule set first, or leave the default alone.
 */
public class FederationDefaultRuleSetChangeBlockedException extends RuntimeException {

    public FederationDefaultRuleSetChangeBlockedException(String message) {
        super(message);
    }
}
