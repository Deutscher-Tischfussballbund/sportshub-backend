package de.dtfb.sportshub.backend.user;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class UserDto {
    private String id;
    private String dtfbId;
    private String email;
    private List<FederationRoleDto> federationRoles;

    @Getter
    @Setter
    public static class FederationRoleDto {
        private String federationId;
        private String federationName;
        private AppRole role;
    }
}