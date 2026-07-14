package de.dtfb.sportshub.backend.federation;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FederationDto {
    private String id;
    private String name;
    /** Optional federation-wide default league rule set (last fallback in rule resolution). */
    private String defaultRuleSetId;
}
