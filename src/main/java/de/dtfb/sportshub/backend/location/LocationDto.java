package de.dtfb.sportshub.backend.location;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class LocationDto {
    private UUID id;
    private String name;
    private String address;
}
