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
    private String gender;
    private String nationalLicense;
    private String nationality;
    private Integer birthYear;
    private boolean active;
    private List<ClubDto> clubs;
}
