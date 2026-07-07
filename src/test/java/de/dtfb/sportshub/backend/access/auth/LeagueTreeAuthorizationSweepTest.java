package de.dtfb.sportshub.backend.access.auth;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Gate sweep for the league tree: every write endpoint from {@code Tier} downwards must be gated.
 * {@code @authz} is mocked, so every gate ({@code canManageLeague}/{@code canManageTier}/
 * {@code canOrganize*}) returns Mockito's default {@code false} — a correctly-gated write therefore
 * returns 403 for an authenticated caller (and 401 without a token). A missing {@code @PreAuthorize}
 * would slip through as 2xx/4xx-other and fail the sweep. (The real cascade and the allow-path live
 * in {@link LeagueAuthorizationIntegrationTest}.)
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeagueTreeAuthorizationSweepTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthorizationService authz;

    static Stream<Arguments> writeEndpoints() {
        String tier = "{\"name\":\"T\",\"leagueId\":\"l\"}";
        String group = "{\"name\":\"G\",\"tierId\":\"t\",\"groupState\":\"READY\"}";
        String round = "{\"name\":\"R\",\"index\":1,\"groupId\":\"g\"}";
        String matchDay = "{\"name\":\"M\",\"roundId\":\"r\"}";
        String match = "{\"matchDayId\":\"md\"}";
        String matchSet = "{\"matchId\":\"m\"}";
        String matchEvent = "{\"type\":\"GOAL\",\"matchId\":\"m\"}";
        return Stream.of(
            write(HttpMethod.POST, "/v1/tiers", tier),
            write(HttpMethod.PUT, "/v1/tiers/x", tier),
            write(HttpMethod.DELETE, "/v1/tiers/x", null),
            write(HttpMethod.POST, "/v1/groups", group),
            write(HttpMethod.PUT, "/v1/groups/x", group),
            write(HttpMethod.DELETE, "/v1/groups/x", null),
            write(HttpMethod.POST, "/v1/rounds", round),
            write(HttpMethod.PUT, "/v1/rounds/x", round),
            write(HttpMethod.DELETE, "/v1/rounds/x", null),
            write(HttpMethod.POST, "/v1/matchdays", matchDay),
            write(HttpMethod.PUT, "/v1/matchdays/x", matchDay),
            write(HttpMethod.DELETE, "/v1/matchdays/x", null),
            write(HttpMethod.POST, "/v1/matches", match),
            write(HttpMethod.PUT, "/v1/matches/x", match),
            write(HttpMethod.DELETE, "/v1/matches/x", null),
            write(HttpMethod.POST, "/v1/matchsets", matchSet),
            write(HttpMethod.PUT, "/v1/matchsets/x", matchSet),
            write(HttpMethod.DELETE, "/v1/matchsets/x", null),
            write(HttpMethod.POST, "/v1/matchevents", matchEvent),
            write(HttpMethod.PUT, "/v1/matchevents/x", matchEvent),
            write(HttpMethod.DELETE, "/v1/matchevents/x", null));
    }

    private static Arguments write(HttpMethod method, String path, String body) {
        return arguments(method, path, body);
    }

    @ParameterizedTest(name = "{0} {1} → 403 for a non-organizer")
    @MethodSource("writeEndpoints")
    void write_whenNotAuthorized_isForbidden(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(body(method, path, body).with(jwt()))
            .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1} → 401 without a token")
    @MethodSource("writeEndpoints")
    void write_withoutToken_isUnauthorized(HttpMethod method, String path, String body) throws Exception {
        mockMvc.perform(body(method, path, body))
            .andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder body(HttpMethod method, String path, String body) {
        MockHttpServletRequestBuilder builder = request(method, path);
        return body == null ? builder : builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
