package de.dtfb.sportshub.backend.support;

import com.jayway.jsonpath.JsonPath;
import de.dtfb.sportshub.backend.club.Club;
import de.dtfb.sportshub.backend.club.ClubRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for controller integration tests that need to run as an authorized admin.
 *
 * <p>Every MockMvc request is authenticated as the seeded bootstrap admin (the dev profile —
 * active in tests — seeds {@code dtfb_id="admin"} with a global ADMIN role). Authentication is
 * applied at the request-builder level (a default request carrying a mock JWT), so it also covers
 * {@code @PostConstruct} setup that issues HTTP calls before any per-test lifecycle hook runs.
 *
 * <p>This exercises the REAL authorization stack: the {@code authenticated()} baseline and
 * {@code @PreAuthorize}/{@code @authz} gates all run for real, with admin rights. Dedicated
 * security tests (e.g. {@code CategoryControllerSecurityTest}) cover non-admin/forbidden cases.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(AuthorizedControllerTest.AdminMockMvcAuth.class)
public abstract class AuthorizedControllerTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private ClubRepository clubRepository;

    /**
     * Create a federation and return its id. A season requires a federation, so any test that
     * sets one up needs a real federation id first.
     */
    protected String createFederation() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/federation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Testverband"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /**
     * Seed a club and return its id. Clubs have no create endpoint (they arrive via import), so a
     * test that needs one — e.g. a team, which always belongs to a club — persists it directly.
     */
    protected String createClub() throws Exception {
        Club club = new Club();
        club.setName("Testverein");
        club.setFederationId(createFederation());
        return clubRepository.save(club).getId();
    }

    /** Create a category and return its id — a discipline requires a category. */
    protected String createCategory() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"name": "Herren", "shortName": "H"}
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    @TestConfiguration
    static class AdminMockMvcAuth {

        /** Attach a mock admin JWT to every request (covers @PostConstruct-time HTTP setup). */
        @Bean
        MockMvcBuilderCustomizer authenticateAsAdmin() {
            return builder -> builder.defaultRequest(
                get("/").with(jwt().jwt(token -> token.claim("dtfb_id", "admin"))));
        }
    }
}
