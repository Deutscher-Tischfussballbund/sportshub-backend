package de.dtfb.sportshub.backend.federation;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class FederationDto {
    private UUID id;
    private String name;
}
