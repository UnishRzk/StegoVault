package com.unish.steganography.crypto;

import com.unish.steganography.exception.InvalidEncryptedPayloadException;
import com.unish.steganography.exception.InvalidPasswordException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

public final class AesGcmCodec {

    public static final int DEFAULT_ITERATIONS = 600_000;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 1024;

    private static final String KEY_DERIVATION_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int TAG_BITS = EncryptedEnvelope.TAG_BYTES * 8;
    private static final String DECRYPTION_FAILED = "Unable to decrypt payload: incorrect password or corrupted data";

    private final SecureRandom random;
    private final int iterations;

    public AesGcmCodec() {
        this(new SecureRandom(), DEFAULT_ITERATIONS);
    }

    AesGcmCodec(SecureRandom random, int iterations) {
        if (iterations < EncryptedEnvelope.MIN_ITERATIONS || iterations > EncryptedEnvelope.MAX_ITERATIONS) {
            throw new IllegalArgumentException("Iteration count is outside the supported range");
        }
        this.random = random;
        this.iterations = iterations;
    }

    public byte[] encrypt(byte[] plaintext, String password) {
        if (plaintext == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        char[] passwordChars = validatePassword(password);
        try {
            byte[] salt = new byte[EncryptedEnvelope.SALT_BYTES];
            random.nextBytes(salt);
            byte[] nonce = new byte[EncryptedEnvelope.NONCE_BYTES];
            random.nextBytes(nonce);

            SecretKey key = deriveKey(passwordChars, salt, iterations);
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            EncryptedEnvelope envelope = new EncryptedEnvelope(iterations, salt, nonce, ciphertext);
            return envelope.toBytes();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to encrypt payload", ex);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    public byte[] decrypt(byte[] envelopeBytes, String password) {
        char[] passwordChars = validatePassword(password);
        try {
            EncryptedEnvelope envelope = EncryptedEnvelope.fromBytes(envelopeBytes);
            SecretKey key = deriveKey(passwordChars, envelope.salt(), envelope.iterations());
            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, envelope.nonce()));
            return cipher.doFinal(envelope.ciphertext());
        } catch (InvalidEncryptedPayloadException ex) {
            throw ex;
        } catch (GeneralSecurityException ex) {
            throw new InvalidEncryptedPayloadException(DECRYPTION_FAILED);
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static SecretKey deriveKey(char[] password, byte[] salt, int iterations)
            throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(KEY_DERIVATION_ALGORITHM);
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(encoded, "AES");
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        } finally {
            spec.clearPassword();
        }
    }

    private static char[] validatePassword(String password) {
        if (password == null) {
            throw new InvalidPasswordException("Password is required");
        }
        if (password.isBlank()) {
            throw new InvalidPasswordException("Password must not be blank");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new InvalidPasswordException(
                    "Password must be between " + MIN_PASSWORD_LENGTH + " and "
                            + MAX_PASSWORD_LENGTH + " characters");
        }
        return password.toCharArray();
    }

}
