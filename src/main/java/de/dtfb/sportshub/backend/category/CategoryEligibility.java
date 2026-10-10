package de.dtfb.sportshub.backend.category;

import de.dtfb.sportshub.backend.player.Player;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The single place a {@link Category}'s eligibility profile is evaluated against a player. Every
 * criterion is nullable (null = unrestricted), and a player must pass all of them. Today the only
 * criterion is {@link Category#getEligibleSide()}; age bounds (U16/U19/seniors, checked against
 * {@code Player.birthYear} relative to the league's season) are the planned next one -- they slot
 * in here as another clause (plus a season parameter and new {@link IneligibilityReason}s), with no
 * change to the callers or the 409 error shape. See docs/19-category-eligibility.md.
 */
@Component
public class CategoryEligibility {

    /**
     * Empty when the player may be rostered in a league of this category. An incomplete player
     * (birth year or gender unknown, docs/28) never may, whatever the category; otherwise a null
     * category passes.
     */
    public Optional<IneligibilityReason> check(Category category, Player player) {
        if (!player.isComplete()) {
            return Optional.of(IneligibilityReason.INCOMPLETE);
        }
        if (category == null) {
            return Optional.empty();
        }
        if (category.getEligibleSide() != null && player.getGender().leagueSide() != category.getEligibleSide()) {
            return Optional.of(IneligibilityReason.WRONG_SIDE);
        }
        return Optional.empty();
    }
}
