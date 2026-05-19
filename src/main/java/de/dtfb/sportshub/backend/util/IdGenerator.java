package de.dtfb.sportshub.backend.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

public class IdGenerator {
    private static final char[] ALPHABET =
        "23456789ABCDEFGHJKLMNOPQRSTUVWXYZ-".toCharArray();

    private static final int SIZE = 14;

    private IdGenerator() {
    }

    public static String newId() {
        return NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            ALPHABET,
            SIZE
        );
    }
}
