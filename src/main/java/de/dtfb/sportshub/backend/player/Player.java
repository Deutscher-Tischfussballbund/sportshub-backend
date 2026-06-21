package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * The federation member behind a Keycloak identity. Created on first login from the
 * JWT ({@code dtfb_id} + {@code email}). Nano-id keyed via {@link BaseEntity}.
 */
@Entity
@Table(name = "player")
@Getter
@Setter
public class Player extends BaseEntity {

    @Column(unique = true, nullable = false)
    private String dtfbId;

    private String email;

    private String firstName;
    private String lastName;
    private String nationalId;
    private String internationalId;
    private String nationality;
    private Integer birthYear;

    /** Wire values: "man" | "woman" (free-form to stay forgiving of the source). */
    private String gender;

    /** National license grade: "A" | "B" | "C" | "D". */
    private String nationalLicense;

    @Column(nullable = false)
    private boolean active = true;
}
