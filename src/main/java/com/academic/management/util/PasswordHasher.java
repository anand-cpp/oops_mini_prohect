package com.academic.management.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Salted SHA-256 hashing for the application's sign-in credentials.
 *
 * <p>No password is ever stored or logged in plaintext. A fresh random
 * salt is generated per user, and the stored value is
 * {@code SHA-256(salt || password)} rendered as lowercase hex.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final int SALT_HEX_LENGTH = 32;
    private static final int SALT_BYTES = SALT_HEX_LENGTH / 2;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // utility class
    }

    /** Generates a new 32-character hexadecimal salt. */
    public static String newSalt() {
        byte[] bytes = new byte[SALT_BYTES];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    /**
     * Hashes {@code plainTextPassword} with {@code salt}.
     *
     * @return lowercase hexadecimal SHA-256 digest
     * @throws IllegalArgumentException if the algorithm is unavailable,
     *         which cannot happen on any standard JRE
     */
    public static String hash(String salt, String plainTextPassword) {
        if (salt == null || plainTextPassword == null) {
            throw new IllegalArgumentException("Salt and password must not be null.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] input = (salt + plainTextPassword).getBytes(StandardCharsets.UTF_8);
            return toHex(digest.digest(input));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(ALGORITHM + " is not available on this JVM.", e);
        }
    }

    /**
     * Constant-time comparison of a candidate password against a stored
     * hash, to avoid leaking information through timing.
     */
    public static boolean matches(String salt, String candidate, String storedHash) {
        if (salt == null || candidate == null || storedHash == null) {
            return false;
        }
        return constantTimeEquals(hash(salt, candidate), storedHash);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length(); i++) {
            difference |= a.charAt(i) ^ b.charAt(i);
        }
        return difference == 0;
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
