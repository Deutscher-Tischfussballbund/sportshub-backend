package de.dtfb.sportshub.backend.user;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The Keycloak-authenticated login identity behind a JWT ({@code dtfb_id}). Permanent and
 * season-independent -- distinct from {@link de.dtfb.sportshub.backend.player.Player}, the
 * competitor record a User may (optionally) be linked to. Holds
 * {@link de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment}s: a role is a property of
 * the login identity, not of the Player record. Table is {@code app_user}, not {@code user} -- a
 * reserved word in most SQL dialects.
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
public class User extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String dtfbId;

    private String email;
    private String firstName;
    private String lastName;
}
