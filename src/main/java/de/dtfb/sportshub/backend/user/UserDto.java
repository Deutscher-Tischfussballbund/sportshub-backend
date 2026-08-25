package de.dtfb.sportshub.backend.user;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto {
    private String id;
    private String dtfbId;
    private String email;
    private String firstName;
    private String lastName;
}
