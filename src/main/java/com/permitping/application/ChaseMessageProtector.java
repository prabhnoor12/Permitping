package com.permitping.application;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

/** Encrypts bearer-link notification payloads before they are persisted in the local outbox. */
public final class ChaseMessageProtector {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public ChaseMessageProtector(String secret) {
        if (secret == null || secret.isBlank()) throw new IllegalArgumentException("A chase encryption key is required");
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception ex) { throw new IllegalStateException("Unable to initialize chase encryption", ex); }
    }

    public static Optional<ChaseMessageProtector> fromEnvironment() {
        String secret = System.getenv("PERMITPING_CHASE_KEY");
        return secret == null || secret.isBlank() ? Optional.empty() : Optional.of(new ChaseMessageProtector(secret));
    }

    public String encrypt(String value) {
        try {
            byte[] nonce = new byte[NONCE_BYTES]; random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance(ALGORITHM); cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] ciphertext = cipher.doFinal((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertext.length]; System.arraycopy(nonce, 0, payload, 0, nonce.length); System.arraycopy(ciphertext, 0, payload, nonce.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) { throw new IllegalStateException("Unable to encrypt chase message", ex); }
    }

    public String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value); if (payload.length <= NONCE_BYTES) throw new IllegalArgumentException("Invalid encrypted chase message");
            byte[] nonce = java.util.Arrays.copyOfRange(payload, 0, NONCE_BYTES); byte[] ciphertext = java.util.Arrays.copyOfRange(payload, NONCE_BYTES, payload.length);
            Cipher cipher = Cipher.getInstance(ALGORITHM); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ex) { throw new IllegalStateException("Unable to decrypt chase message", ex); }
    }
}
