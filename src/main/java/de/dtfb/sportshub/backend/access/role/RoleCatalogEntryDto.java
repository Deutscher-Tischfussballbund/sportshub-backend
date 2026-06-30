package de.dtfb.sportshub.backend.access.role;

/**
 * One entry in the role catalog: the role, the scope kind a grant of it implies, and whether it is
 * deprecated (kept for existing assignments but not offered for new grants). Serialized via the
 * enums' {@code @JsonValue} wire forms (e.g. {@code "region_admin"} / {@code "region"}).
 */
public record RoleCatalogEntryDto(Role role, ScopeType scopeType, boolean deprecated) {
}
