package com.permitping.application;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class PasswordHasher {
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 600_000, SALT_BYTES = 16, KEY_BITS = 256;
    private final SecureRandom random = new SecureRandom();
    public String hash(char[] password) {
        try { requirePassword(password); byte[] salt = new byte[SALT_BYTES]; random.nextBytes(salt);
            return format(salt, derive(password, salt, ITERATIONS));
        } finally { if (password != null) java.util.Arrays.fill(password, '\0'); }
    }
    public boolean matches(char[] password, String encoded) {
        if (password == null || encoded == null) return false;
        try { String[] parts = encoded.split("\\$", -1); if (parts.length != 4 || !parts[0].equals("pbkdf2_sha256")) return false;
            int iterations = Integer.parseInt(parts[1]); byte[] salt = Base64.getDecoder().decode(parts[2]); byte[] expected = Base64.getDecoder().decode(parts[3]);
            if (iterations < 100_000 || iterations > 2_000_000 || salt.length < SALT_BYTES || expected.length != KEY_BITS / 8) return false;
            return MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (RuntimeException ex) { return false; } finally { java.util.Arrays.fill(password, '\0'); }
    }
    private byte[] derive(char[] password, byte[] salt, int iterations) { try { return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(new PBEKeySpec(password, salt, iterations, KEY_BITS)).getEncoded(); } catch (Exception ex) { throw new IllegalStateException("Password hashing is unavailable", ex); } }
    private String format(byte[] salt, byte[] hash) { return "pbkdf2_sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash); }
    private void requirePassword(char[] password) { if (password == null || password.length < 12) throw new IllegalArgumentException("Password must be at least 12 characters"); }
}
