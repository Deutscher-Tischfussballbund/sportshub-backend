package de.dtfb.sportshub.backend.player;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Player numbers (docs/24, docs/28): every player holds exactly one current number, old numbers stay
 * as aliases. Real numbers come from the Sports Manager; missing ones are issued here under a prefix
 * by reason ({@link PlayerNumberKind}), zero-padded to four digits like real numbers but never assumed
 * to be four digits wide.
 */
@Service
public class PlayerNumberService {

    private static final int PADDED_DIGITS = 4;

    private final PlayerNumberRepository numberRepository;
    private final PlayerNumberSequenceRepository sequenceRepository;
    private final PlayerRepository playerRepository;

    public PlayerNumberService(PlayerNumberRepository numberRepository,
                               PlayerNumberSequenceRepository sequenceRepository,
                               PlayerRepository playerRepository) {
        this.numberRepository = numberRepository;
        this.sequenceRepository = sequenceRepository;
        this.playerRepository = playerRepository;
    }

    /** The player holding or having held {@code number}, if any. */
    @Transactional(readOnly = true)
    public Optional<Player> findPlayer(String number) {
        return numberRepository.findByNumber(number).map(PlayerNumber::getPlayer);
    }

    @Transactional(readOnly = true)
    public List<PlayerNumber> numbers(String playerId) {
        return numberRepository.findByPlayerIdOrderByValidFromAsc(playerId);
    }

    /**
     * Makes {@code number} the player's current number; the previous one becomes an alias. A no-op if
     * it already is. Refused if the number belongs to another player -- numbers are never reused.
     */
    @Transactional
    public void assign(Player player, String number, PlayerNumberKind kind) {
        Optional<PlayerNumber> existing = numberRepository.findByNumber(number);
        if (existing.isPresent() && !existing.get().getPlayer().getId().equals(player.getId())) {
            throw new IllegalStateException("Player number " + number + " belongs to another player");
        }
        Optional<PlayerNumber> current = numberRepository.findByPlayerIdAndValidToIsNull(player.getId());
        if (current.isPresent() && current.get().getNumber().equals(number)) {
            return;
        }
        Instant now = Instant.now();
        current.ifPresent(previous -> {
            previous.setValidTo(now);
            numberRepository.save(previous);
        });
        PlayerNumber assigned = existing.orElseGet(PlayerNumber::new);
        assigned.setPlayer(player);
        assigned.setNumber(number);
        assigned.setKind(kind);
        assigned.setValidFrom(now);
        assigned.setValidTo(null);
        numberRepository.save(assigned);

        player.setNationalId(number);
        playerRepository.save(player);
    }

    /** Issues the next number of {@code kind}'s prefix to the player and makes it current. */
    @Transactional
    public String issue(Player player, PlayerNumberKind kind) {
        if (kind.prefix() == null) {
            throw new IllegalArgumentException("Real numbers are issued by the DTFB, not the Sports Hub");
        }
        String number;
        do {
            number = format(kind.prefix(), nextValue(kind.prefix()));
        } while (numberRepository.findByNumber(number).isPresent());
        assign(player, number, kind);
        return number;
    }

    /**
     * One-off: a player with a {@link Player#getNationalId()} but no {@link PlayerNumber} row (seeded
     * or older data) gets that number recorded as their current one. A number already recorded for
     * someone else is skipped. Returns how many were filled.
     */
    @Transactional
    public int backfill() {
        int filled = 0;
        for (Player player : playerRepository.findAll()) {
            if (player.getNationalId() == null || player.getNationalId().isBlank()
                || numberRepository.existsByPlayerId(player.getId())
                // Older data may carry the same number twice; the first player keeps it.
                || numberRepository.findByNumber(player.getNationalId()).isPresent()) {
                continue;
            }
            PlayerNumber number = new PlayerNumber();
            number.setPlayer(player);
            number.setNumber(player.getNationalId());
            number.setKind(kindOf(player.getNationalId()));
            number.setValidFrom(Instant.now());
            numberRepository.save(number);
            filled++;
        }
        return filled;
    }

    /** The kind a number was issued as, read from its prefix. */
    public static PlayerNumberKind kindOf(String number) {
        for (PlayerNumberKind kind : PlayerNumberKind.values()) {
            if (kind.prefix() != null && number.startsWith(kind.prefix() + "-")) {
                return kind;
            }
        }
        return PlayerNumberKind.REAL;
    }

    static String format(String prefix, long value) {
        String digits = Long.toString(value);
        return prefix + "-" + "0".repeat(Math.max(0, PADDED_DIGITS - digits.length())) + digits;
    }

    private long nextValue(String prefix) {
        PlayerNumberSequence sequence = sequenceRepository.lockByPrefix(prefix).orElseGet(() -> {
            PlayerNumberSequence created = new PlayerNumberSequence();
            created.setPrefix(prefix);
            created.setNextValue(1);
            return created;
        });
        long value = sequence.getNextValue();
        sequence.setNextValue(value + 1);
        sequenceRepository.save(sequence);
        return value;
    }
}
