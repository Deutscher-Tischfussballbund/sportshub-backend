package de.dtfb.sportshub.backend.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User findOrCreateByDtfbId(String dtfbId, String email) {
        return userRepository.findByDtfbId(dtfbId).orElseGet(() -> {
            User user = new User();
            user.setDtfbId(dtfbId);
            user.setEmail(email);
            return userRepository.save(user);
        });
    }

    public User findByDtfbId(String dtfbId) {
        return userRepository.findByDtfbId(dtfbId)
            .orElseThrow(() -> new UserNotFoundException(dtfbId));
    }

    public UserDto toDto(User user) {
        UserDto dto = new UserDto();
        dto.setId(user.getId());
        dto.setDtfbId(user.getDtfbId());
        dto.setEmail(user.getEmail());
        dto.setFederationRoles(user.getFederationRoles().stream().map(r -> {
            UserDto.FederationRoleDto roleDto = new UserDto.FederationRoleDto();
            roleDto.setFederationId(r.getFederation().getId());
            roleDto.setFederationName(r.getFederation().getName());
            roleDto.setRole(r.getRole());
            return roleDto;
        }).toList());
        return dto;
    }
}