package de.dtfb.sportshub.backend.config.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class KeyCloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    @SuppressWarnings("unchecked")
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> realmAuthorities = new ArrayList<>();
        Collection<GrantedAuthority> resourceAuthorities = new ArrayList<>();
        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // 1. collect realm roles
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null && realmAccess.containsKey("roles")) {
            List<String> realmRoles = (List<String>) realmAccess.get("roles");
            realmRoles.forEach(roleName -> realmAuthorities.add(new SimpleGrantedAuthority(roleName)));
        }
        // 2. collect client roles
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null && resourceAccess.containsKey("api-backend")) {
            Map<String, Object> clientRoles = (Map<String, Object>) resourceAccess.get("api-backend");
            if (clientRoles.containsKey("roles")) {
                List<String> roles = (List<String>) clientRoles.get("roles");
                roles.forEach(roleName -> resourceAuthorities.add(new SimpleGrantedAuthority(roleName)));
            }
        }
        authorities.addAll(realmAuthorities);
        authorities.addAll(resourceAuthorities);
        return authorities;
    }
}
