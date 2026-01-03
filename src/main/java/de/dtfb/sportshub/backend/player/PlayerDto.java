package de.dtfb.sportshub.backend.player;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class PlayerDto {
    private UUID id;
    private String firstName;
    private String lastName;
}
