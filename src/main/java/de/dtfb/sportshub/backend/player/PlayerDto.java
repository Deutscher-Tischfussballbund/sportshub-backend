package de.dtfb.sportshub.backend.player;

import de.dtfb.sportshub.backend.club.ClubDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * The canonical player representation. Carries the full profile the admin frontend
 * needs; the external-API fetch ({@code GET /v1/players/{id}}) populates the
 * subset it returns and leaves the rest null. No-arg + all-args constructors keep
 * both Jackson deserialization and direct construction working.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PlayerDto {
    private String id;
    private String firstName;
    private String lastName;
    private String nationalId;
    private String internationalId;
    /** Public gender -- divers without its league side. Always populated when known. */
    private Gender gender;
    /**
     * Full stored gender including the divers league side. Admin-only: populated for callers with
     * any admin role, null for everyone else (see {@link PlayerGenderVisibility}). The write path
     * ({@code PUT /v1/admin/players/{id}}) reads this field, not {@link #gender}.
     */
    private PlayerGender genderDetail;
    private String nationalLicense;
    private String nationality;
    private Integer birthYear;
    private boolean active;
    /**
     * Birth year and gender known -- only complete players can be rostered (docs/28). Read-only; a
     * wrapper so update bodies without it still deserialize.
     */
    private Boolean complete;
    private List<ClubDto> clubs;
}
