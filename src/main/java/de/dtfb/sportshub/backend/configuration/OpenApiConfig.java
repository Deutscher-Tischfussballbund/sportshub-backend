package de.dtfb.sportshub.backend.configuration;

import de.dtfb.sportshub.backend.access.apikey.ApiKeyAuthenticationFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Swagger / OpenAPI presentation:
 * <ul>
 *   <li><b>Auth</b> — interchangeable schemes so "Try it out" works against secured endpoints:
 *       paste a token ({@code bearer-jwt}) or log in via Keycloak ({@code keycloak},
 *       authorization-code + PKCE; the client id / PKCE flag are set in {@code application-dev.yaml}).
 *       Additionally a read-only API key ({@code api-key}, header {@code X-API-Key}, docs/20) — offered
 *       only on the operations a key may call (see {@link #apiKeyOnReadEndpoints()}).</li>
 *   <li><b>Tags</b> — the auto-generated {@code *-controller} group names are prettified
 *       (e.g. {@code match-day-controller} → {@code Match Day}).</li>
 * </ul>
 *
 * <p>The shared error responses (400/403/404/500/503, all the {@code ApiError} body) are <i>not</i>
 * wired here: springdoc derives them from {@code GlobalExceptionHandler}'s {@code @ExceptionHandler}
 * methods (which all return {@code ApiError}) and applies them to every operation
 * ({@code springdoc.override-with-generic-response}, on by default).
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearer-jwt";
    private static final String OAUTH_SCHEME = "keycloak";
    private static final String API_KEY_SCHEME = "api-key";

    /**
     * Group display order in Swagger UI — follows the domain chain (org tree, then competition tree,
     * then identity/admin), instead of the default alphabetical/discovery order. Tags not listed here
     * are appended alphabetically, so a new controller is never hidden — just unordered until added.
     */
    private static final List<String> TAG_ORDER = List.of(
        "Federation", "Region", "Club", "Team",
        "Season", "League", "Category", "Location", "League Rule Set",
        "Tier", "Group", "Round", "Match Day", "Match", "Match Set", "Match Event", "Standing",
        "Player", "Player Admin",
        "Auth Me", "Role Admin", "Role Catalog");

    /** One-line blurb per group, shown under the tag header in Swagger UI. */
    private static final Map<String, String> TAG_DESCRIPTIONS = Map.ofEntries(
        Map.entry("Federation", "Landesverbände — the top of the federation tree. Admin-managed."),
        Map.entry("Region", "Read alias for federations (a region is a Landesverband)."),
        Map.entry("Club", "Vereine within a region. Read-only — clubs arrive via import."),
        Map.entry("Team", "Teams within a club. Managed by the club's or region's admin; every team belongs to a club."),
        Map.entry("Season", "Spielzeiten, scoped to a region. Managed by that region's admin."),
        Map.entry("League", "Leagues within a season (one category's ladder). Region-admin managed."),
        Map.entry("Category", "Global classifications (e.g. Herren). Admin-managed."),
        Map.entry("Location", "Venues, optionally tied to a region. Region- or admin-managed."),
        Map.entry("League Rule Set", "Reusable league rules (points, game plan, scoring). Region-owned; null = DTFB template."),
        Map.entry("Tier", "Promotion/relegation levels within a league (e.g. 1. Bayernliga). Region-admin managed."),
        Map.entry("Group", "Round-robin groups within a tier. Organizer-managed."),
        Map.entry("Round", "Rounds within a group. Organizer-managed."),
        Map.entry("Match Day", "Match days within a round, plus the team-representative result submit/confirm flow."),
        Map.entry("Match", "Individual matches within a match day. Organizer-managed."),
        Map.entry("Match Set", "Sets within a match. Organizer-managed."),
        Map.entry("Match Event", "Point / timeout / … events within a match. Organizer-managed."),
        Map.entry("Standing", "Computed group standings. Read-only."),
        Map.entry("Player", "Single player lookup."),
        Map.entry("Player Admin", "Member directory for the admin frontend."),
        Map.entry("Auth Me", "The caller's own identity, roles and manageable areas."),
        Map.entry("Role Admin", "Grant / revoke scoped role assignments and look them up."),
        Map.entry("Role Catalog", "The static catalog of roles and the scope each implies."));

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Bean
    public OpenAPI dtfbOpenAPI() {
        String openIdConnect = issuerUri + "/protocol/openid-connect";
        return new OpenAPI()
            .info(new Info()
                .title("DTFB Sportshub API")
                .version("v1")
                .description("Backend for the DTFB federation ecosystem. Authorize with a Keycloak "
                    + "token (paste it, or use the Keycloak login) to try secured endpoints. Read "
                    + "endpoints can also be tried with a read-only API key (X-API-Key), created by a "
                    + "global admin in the admin app."))
            .components(new Components()
                .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Paste a Keycloak access token (without the \"Bearer \" prefix)."))
                .addSecuritySchemes(OAUTH_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .description("Log in via Keycloak (authorization code + PKCE).")
                    .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                        .authorizationUrl(openIdConnect + "/auth")
                        .tokenUrl(openIdConnect + "/token")
                        .scopes(new Scopes().addString("openid", "OpenID Connect")))))
                .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name(ApiKeyAuthenticationFilter.HEADER)
                    .description("Read-only API key (docs/20-api-keys.md): GET/HEAD only, never "
                        + "/v1/admin/** or /v1/auth/**. Don't combine it with a token -- a request "
                        + "carrying both is rejected (400). Errors: 401 INVALID_API_KEY, "
                        + "403 API_KEY_READ_ONLY / API_KEY_PATH_NOT_ALLOWED.")))
            // Either scheme satisfies the requirement (separate items = OR).
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME));
    }

    /**
     * Offers the API key only where {@code ApiKeyAuthenticationFilter} accepts one: GET/HEAD outside
     * {@code /v1/admin/**} and {@code /v1/auth/**}. Everywhere else the operation keeps the global
     * token-only requirement, so Swagger UI never sends a key that would be refused.
     */
    @Bean
    public OpenApiCustomizer apiKeyOnReadEndpoints() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith("/v1/admin/") || path.startsWith("/v1/auth/")) {
                    return;
                }
                pathItem.readOperationsMap().forEach((method, operation) -> {
                    if (method == PathItem.HttpMethod.GET || method == PathItem.HttpMethod.HEAD) {
                        operation.setSecurity(List.of(
                            new SecurityRequirement().addList(BEARER_SCHEME),
                            new SecurityRequirement().addList(OAUTH_SCHEME),
                            new SecurityRequirement().addList(API_KEY_SCHEME)));
                    }
                });
            });
        };
    }

    /**
     * Documents the 401 on every operation behind the main JWT chain. It comes from Spring Security's
     * entry point ({@link ApiErrorAuthenticationEntryPoint}), not from GlobalExceptionHandler, so
     * springdoc can't derive it the way it derives the other shared error responses. The tracker's
     * own chain ({@code /v1/tracker/issues/**}, {@code /v1/tracker/me}) is public and excluded.
     */
    @Bean
    public OpenApiCustomizer documentUnauthorized() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                if (path.startsWith("/v1/tracker/issues") || path.equals("/v1/tracker/me")) {
                    return;
                }
                pathItem.readOperations().forEach(operation -> {
                    if (operation.getResponses() == null) {
                        operation.setResponses(new ApiResponses());
                    }
                    operation.getResponses().addApiResponse("401", new ApiResponse()
                        .description("No or invalid credentials (missing/expired token, or an unknown, "
                            + "deactivated or expired API key)")
                        .content(new Content().addMediaType("application/json",
                            new MediaType().schema(new Schema<>().$ref("#/components/schemas/ApiError")))));
                });
            });
        };
    }

    @Bean
    public OpenApiCustomizer organizeTags() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            // 1. Prettify each operation's tag(s): "match-day-controller" → "Match Day".
            openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .filter(operation -> operation.getTags() != null)
                .forEach(operation -> operation.setTags(
                    operation.getTags().stream().map(this::prettifyTag).distinct().toList()));

            // 2. Emit a top-level tags list in domain-chain order — Swagger UI renders groups in
            //    this order. Known tags first (TAG_ORDER), then any leftovers alphabetically.
            Set<String> present = openApi.getPaths().values().stream()
                .flatMap(pathItem -> pathItem.readOperations().stream())
                .filter(operation -> operation.getTags() != null)
                .flatMap(operation -> operation.getTags().stream())
                .collect(Collectors.toCollection(TreeSet::new));

            List<String> ordered = new ArrayList<>();
            TAG_ORDER.forEach(tag -> {
                if (present.remove(tag)) {
                    ordered.add(tag);
                }
            });
            ordered.addAll(present); // leftover tags, alphabetical (TreeSet order)

            openApi.setTags(ordered.stream()
                .map(name -> new Tag().name(name).description(TAG_DESCRIPTIONS.get(name)))
                .toList());
        };
    }

    /** {@code "match-day-controller"} → {@code "Match Day"}. */
    private String prettifyTag(String rawTag) {
        String withoutSuffix = rawTag.endsWith("-controller")
            ? rawTag.substring(0, rawTag.length() - "-controller".length())
            : rawTag;
        return Arrays.stream(withoutSuffix.split("[-_]"))
            .filter(word -> !word.isBlank())
            .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
            .collect(Collectors.joining(" "));
    }
}
