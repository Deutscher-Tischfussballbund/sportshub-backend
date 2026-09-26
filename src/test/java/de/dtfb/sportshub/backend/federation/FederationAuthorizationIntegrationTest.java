package de.dtfb.sportshub.backend.federation;

import de.dtfb.sportshub.backend.access.role.Role;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignment;
import de.dtfb.sportshub.backend.access.roleassignment.RoleAssignmentRepository;
import de.dtfb.sportshub.backend.user.User;
import de.dtfb.sportshub.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A region admin manages their own federation (e.g. picking its default rule set, from the
 * rule-set dialog's "Default" checkbox) -- {@code PUT /v1/federations/{id}} used to be
 * global-admin-only, refusing every region admin's attempt with a 403, even for their own
 * federation. Fixed to {@code canManageRegion}, with a parent-federation change still refused
 * unless the caller is a global admin (see docs/16-root-federation.md).
 */
@SpringBootTest
@AutoConfigureMockMvc
class FederationAuthorizationIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    FederationRepository federationRepository;

    @Test
    void regionAdmin_maySetTheirOwnFederationsDefaultRuleSet() throws Exception {
        Federation own = federation("Eigener Verband");
        RequestPostProcessor regionAdmin = grantRegionAdmin("owncomer", own.getId());

        mockMvc.perform(put("/v1/federations/" + own.getId()).with(regionAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Eigener Verband\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void regionAdmin_mayNotUpdateAForeignFederation() throws Exception {
        Federation foreign = federation("Fremder Verband");
        Federation own = federation("Eigener Verband 2");
        RequestPostProcessor regionAdmin = grantRegionAdmin("foreigner", own.getId());

        mockMvc.perform(put("/v1/federations/" + foreign.getId()).with(regionAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Umbenannt\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void regionAdmin_mayNotChangeTheirFederationsParent() throws Exception {
        Federation own = federation("Eigener Verband 3");
        Federation otherParent = federation("Anderer Verband");
        RequestPostProcessor regionAdmin = grantRegionAdmin("reparenter", own.getId());

        mockMvc.perform(put("/v1/federations/" + own.getId()).with(regionAdmin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Eigener Verband 3\",\"parentFederationId\":\"" + otherParent.getId() + "\"}"))
            .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private static RequestPostProcessor jwtFor(String dtfbId) {
        return jwt().jwt(token -> token.claim("dtfb_id", dtfbId));
    }

    private static User user(String dtfbId) {
        User user = new User();
        user.setDtfbId(dtfbId);
        return user;
    }

    private Federation federation(String name) {
        Federation fed = new Federation();
        fed.setName(name);
        return federationRepository.save(fed);
    }

    private RequestPostProcessor grantRegionAdmin(String dtfbId, String federationId) {
        User user = userRepository.save(user(dtfbId));
        RoleAssignment grant = new RoleAssignment();
        grant.setUser(user);
        grant.setRole(Role.REGION_ADMIN);
        grant.setScopeType(ScopeType.REGION);
        grant.setScopeId(federationId);
        grant.setCreatedAt(Instant.now());
        roleAssignmentRepository.save(grant);
        return jwtFor(dtfbId);
    }
}
