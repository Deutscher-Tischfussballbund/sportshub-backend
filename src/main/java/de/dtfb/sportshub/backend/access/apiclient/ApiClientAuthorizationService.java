package de.dtfb.sportshub.backend.access.apiclient;

import de.dtfb.sportshub.backend.access.auth.CompetitionResolver;
import de.dtfb.sportshub.backend.access.role.ScopeType;
import de.dtfb.sportshub.backend.federation.Federation;
import de.dtfb.sportshub.backend.league.League;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Authorization for registered apps/service clients (JWT {@code azp}, no {@code dtfb_id}) --
 * exposed to {@code @PreAuthorize} SpEL as {@code @apiClientAuthz}. Deliberately a sibling of
 * {@link de.dtfb.sportshub.backend.access.auth.AuthorizationService}, not a modification of it:
 * apps are authorized via {@link ApiClientGrant} (keyed on {@code azp}), humans via
 * {@code RoleAssignment} (keyed on {@code dtfb_id}) -- two independent lookup paths, per
 * docs/10-api-consumers-and-authz.md §5. A request either has a {@code dtfb_id} (routes through
 * the human model) or doesn't (routes through this one); the two are combined at the call site via
 * SpEL {@code or}, e.g. {@code @authz.canOrganizeMatch(#id) or @apiClientAuthz.canOrganizeMatch(#id)}.
 *
 * <p>MVP scope is deliberately one wired use case (match-result reporting) rather than every
 * existing gate understanding two principal types -- see docs/10 §2 (core vs. screen-shaped
 * endpoints): machine consumers get a reviewed slice, not a blanket retrofit.
 */
@Component("apiClientAuthz")
public class ApiClientAuthorizationService {

    private final ApiClientGrantRepository grantRepository;
    private final CompetitionResolver competitionResolver;

    public ApiClientAuthorizationService(ApiClientGrantRepository grantRepository,
                                         CompetitionResolver competitionResolver) {
        this.grantRepository = grantRepository;
        this.competitionResolver = competitionResolver;
    }

    @Transactional
    public boolean canOrganizeMatch(String matchId) {
        Optional<ApiClientGrant> maybeGrant = currentGrant();
        if (maybeGrant.isEmpty()) {
            return false;
        }
        ApiClientGrant grant = maybeGrant.get();
        grant.setLastUsedAt(Instant.now());

        if (!grant.isWriteAccess()) {
            return false;
        }
        League league = competitionResolver.ofMatch(matchId);
        if (league == null) {
            return false;
        }
        return inScope(grant, league);
    }

    private boolean inScope(ApiClientGrant grant, League league) {
        if (grant.getScopeType() == ScopeType.GLOBAL) {
            return true;
        }
        if (grant.getScopeType() != ScopeType.REGION) {
            return false;
        }
        Federation region = league.getSeason() == null ? null : league.getSeason().getFederation();
        return region != null && Objects.equals(grant.getScopeId(), region.getId());
    }

    private Optional<ApiClientGrant> currentGrant() {
        String azp = currentAzp();
        if (azp == null) {
            return Optional.empty();
        }
        return grantRepository.findByClientId(azp).filter(ApiClientGrant::isActive);
    }

    private String currentAzp() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("azp");
        }
        return null;
    }
}
