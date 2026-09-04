package com.jobpilot.common.util;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.Clock;

public final class UlidGenerator {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private UlidGenerator() {
    }

    public static String next() {
        byte[] bytes = new byte[16];
        long timestamp = Clock.systemUTC().millis();
        for (int i = 5; i >= 0; i--) {
            bytes[i] = (byte) timestamp;
            timestamp >>>= 8;
        }
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        System.arraycopy(randomness, 0, bytes, 6, randomness.length);
        BigInteger value = new BigInteger(1, bytes);
        char[] encoded = new char[26];
        for (int i = 25; i >= 0; i--) {
            encoded[i] = ALPHABET[value.and(BigInteger.valueOf(31)).intValue()];
            value = value.shiftRight(5);
        }
        return new String(encoded);
    }
}

