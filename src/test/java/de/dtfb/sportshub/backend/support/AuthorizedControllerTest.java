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
     * sets one up needs a real federation id first. No {@code parentFederationId} needed -- it
     * attaches under the seeded root {@code fed-dtfb} automatically (see
     * {@code FederationService#resolveParentForCreate}).
     */
    protected String createFederation() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/federations")
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
        return createClub(createFederation());
    }

    /** Seed a club under a caller-supplied federation (e.g. so a team and its season share one). */
    protected String createClub(String federationId) {
        Club club = new Club();
        club.setName("Testverein");
        club.setFederationId(federationId);
        return clubRepository.save(club).getId();
    }

    /**
     * Join a player to a club — the precondition {@code RosterService#addPlayer} now enforces
     * before a player can be added to one of that club's team rosters. Call this before every
     * roster-add in a test that wasn't already doing so.
     */
    protected void joinClub(String playerId, String clubId) throws Exception {
        mockMvc.perform(post("/v1/admin/clubs/" + clubId + "/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"playerId": "%s"}
                    """, playerId)))
            .andExpect(status().isCreated());
    }

    /** Create a season under {@code federationId} and return its id — a team now requires one. */
    protected String createSeasonId(String federationId) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/seasons")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "2025", "federationId": "%s"}
                    """, federationId)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    /** Create a category and return its id — a discipline requires a category. */
    protected String createCategory() throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(String.format("""
                    {"name": "Herren", "shortName": "%s"}
                    """, TestIds.unique("H"))))
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
