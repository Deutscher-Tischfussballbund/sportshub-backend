package de.dtfb.sportshub.backend.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unique values for fixtures that hit a unique constraint (e.g. {@code Category.shortName}) --
 * test classes share one H2 database per run, so a literal like "H" collides across classes.
 */
public final class TestIds {

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private TestIds() {
    }

    public static String unique(String prefix) {
        return prefix + "-" + COUNTER.incrementAndGet();
    }
}
