package de.dtfb.sportshub.backend.access.apiclient;

import de.dtfb.sportshub.backend.access.role.ScopeType;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class ApiClientGrantDto {
    private String id;
    private String clientId;
    private String name;
    private boolean writeAccess;
    private ScopeType scopeType;
    private String scopeId;
    private boolean active;
    private Instant createdAt;
    private Instant lastUsedAt;
}
