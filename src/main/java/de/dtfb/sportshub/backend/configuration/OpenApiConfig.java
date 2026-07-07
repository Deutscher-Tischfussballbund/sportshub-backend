package de.dtfb.sportshub.backend.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.info.Info;
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
 *   <li><b>Auth</b> — two interchangeable schemes so "Try it out" works against secured endpoints:
 *       paste a token ({@code bearer-jwt}) or log in via Keycloak ({@code keycloak},
 *       authorization-code + PKCE; the client id / PKCE flag are set in {@code application-dev.yaml}).</li>
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
                    + "token (paste it, or use the Keycloak login) to try secured endpoints."))
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
                        .scopes(new Scopes().addString("openid", "OpenID Connect"))))))
            // Either scheme satisfies the requirement (separate items = OR).
            .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME))
            .addSecurityItem(new SecurityRequirement().addList(OAUTH_SCHEME));
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
