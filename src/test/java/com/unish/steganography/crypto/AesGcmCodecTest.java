package com.unish.steganography.crypto;

import com.unish.steganography.exception.InvalidEncryptedPayloadException;
import com.unish.steganography.exception.InvalidPasswordException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesGcmCodecTest {

    private static final String PASSWORD = "correct horse battery staple";
    private static final String DECRYPTION_FAILED =
            "Unable to decrypt payload: incorrect password or corrupted data";

    private final AesGcmCodec codec = new AesGcmCodec();

    @Test
    void roundTripsPlaintext() {
        byte[] plaintext = "top secret".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = codec.encrypt(plaintext, PASSWORD);

        assertArrayEquals(plaintext, codec.decrypt(envelope, PASSWORD));
    }

    @Test
    void roundTripsUnicodePlaintext() {
        byte[] plaintext = "नमस्ते 🌍 — encrypted".getBytes(StandardCharsets.UTF_8);

        byte[] envelope = codec.encrypt(plaintext, PASSWORD);

        assertArrayEquals(plaintext, codec.decrypt(envelope, PASSWORD));
    }

    @Test
    void roundTripsEmptyPlaintext() {
        byte[] envelope = codec.encrypt(new byte[0], PASSWORD);

        assertEquals(0, codec.decrypt(envelope, PASSWORD).length);
    }

    @Test
    void usesVersionedEnvelopeWithDefaultIterations() {
        byte[] envelopeBytes = codec.encrypt("data".getBytes(StandardCharsets.UTF_8), PASSWORD);

        EncryptedEnvelope envelope = EncryptedEnvelope.fromBytes(envelopeBytes);

        assertEquals(EncryptedEnvelope.VERSION, Byte.toUnsignedInt(envelopeBytes[4]));
        assertEquals(AesGcmCodec.DEFAULT_ITERATIONS, envelope.iterations());
        assertEquals(EncryptedEnvelope.SALT_BYTES, envelope.salt().length);
        assertEquals(EncryptedEnvelope.NONCE_BYTES, envelope.nonce().length);
        assertTrue(envelope.ciphertext().length >= EncryptedEnvelope.TAG_BYTES);
    }

    @Test
    void usesFreshSaltAndNonceForEveryEncryption() {
        byte[] plaintext = "same message".getBytes(StandardCharsets.UTF_8);

        EncryptedEnvelope first = EncryptedEnvelope.fromBytes(codec.encrypt(plaintext, PASSWORD));
        EncryptedEnvelope second = EncryptedEnvelope.fromBytes(codec.encrypt(plaintext, PASSWORD));

        assertFalse(Arrays.equals(first.salt(), second.salt()));
        assertFalse(Arrays.equals(first.nonce(), second.nonce()));
        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        byte[] envelope = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> codec.decrypt(envelope, "wrong password value"));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
        assertFalse(ex.getMessage().contains(PASSWORD));
        assertFalse(ex.getMessage().toLowerCase().contains("tag"));
    }

    @Test
    void rejectsTamperedCiphertextWithSameGenericMessage() {
        byte[] envelope = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);
        envelope[envelope.length - 1] ^= 0x01;

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> codec.decrypt(envelope, PASSWORD));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
    }

    @Test
    void rejectsTamperedNonceWithSameGenericMessage() {
        byte[] envelope = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);
        envelope[27] ^= 0x01;

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> codec.decrypt(envelope, PASSWORD));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
    }

    @Test
    void rejectsTamperedSaltWithSameGenericMessage() {
        byte[] envelope = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);
        envelope[10] ^= 0x01;

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> codec.decrypt(envelope, PASSWORD));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
    }

    @Test
    void rejectsNullAndTruncatedEnvelopes() {
        byte[] valid = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);

        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(null));
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(new byte[0]));
        assertThrows(InvalidEncryptedPayloadException.class,
                () -> EncryptedEnvelope.fromBytes(Arrays.copyOf(valid, 20)));
    }

    @Test
    void rejectsBadMagicAndUnsupportedVersion() {
        byte[] valid = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);

        byte[] badMagic = valid.clone();
        badMagic[0] ^= 0x01;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(badMagic));

        byte[] badVersion = valid.clone();
        badVersion[4] = 2;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(badVersion));
    }

    @Test
    void rejectsInvalidIterationCountsBeforeKeyDerivation() {
        byte[] valid = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);

        byte[] tooFew = valid.clone();
        tooFew[5] = 0;
        tooFew[6] = 0;
        tooFew[7] = 0x03;
        tooFew[8] = (byte) 0xE8;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(tooFew));

        byte[] tooMany = valid.clone();
        tooMany[5] = 0x7F;
        tooMany[6] = (byte) 0xFF;
        tooMany[7] = (byte) 0xFF;
        tooMany[8] = (byte) 0xFF;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(tooMany));
    }

    @Test
    void rejectsInvalidLengthFieldsBeforeAllocatingCiphertext() {
        byte[] valid = codec.encrypt("secret".getBytes(StandardCharsets.UTF_8), PASSWORD);

        byte[] badSaltLength = valid.clone();
        badSaltLength[9] = 15;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(badSaltLength));

        byte[] badNonceLength = valid.clone();
        badNonceLength[26] = 11;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(badNonceLength));

        byte[] badTagLength = valid.clone();
        badTagLength[39] = 12;
        assertThrows(InvalidEncryptedPayloadException.class, () -> EncryptedEnvelope.fromBytes(badTagLength));

        byte[] oversizedCiphertext = valid.clone();
        oversizedCiphertext[40] = 0x7F;
        oversizedCiphertext[41] = (byte) 0xFF;
        oversizedCiphertext[42] = (byte) 0xFF;
        oversizedCiphertext[43] = (byte) 0xFF;
        assertThrows(InvalidEncryptedPayloadException.class,
                () -> EncryptedEnvelope.fromBytes(oversizedCiphertext));

        byte[] negativeCiphertext = valid.clone();
        negativeCiphertext[40] = (byte) 0xFF;
        negativeCiphertext[41] = (byte) 0xFF;
        negativeCiphertext[42] = (byte) 0xFF;
        negativeCiphertext[43] = (byte) 0xFF;
        assertThrows(InvalidEncryptedPayloadException.class,
                () -> EncryptedEnvelope.fromBytes(negativeCiphertext));
    }

    @Test
    void rejectsInvalidPasswords() {
        byte[] plaintext = "secret".getBytes(StandardCharsets.UTF_8);

        assertThrows(InvalidPasswordException.class, () -> codec.encrypt(plaintext, null));
        assertThrows(InvalidPasswordException.class, () -> codec.encrypt(plaintext, ""));
        assertThrows(InvalidPasswordException.class, () -> codec.encrypt(plaintext, "short"));
        assertThrows(InvalidPasswordException.class, () -> codec.encrypt(plaintext, "        "));
        assertThrows(InvalidPasswordException.class,
                () -> codec.encrypt(plaintext, "a".repeat(AesGcmCodec.MAX_PASSWORD_LENGTH + 1)));

        byte[] envelope = codec.encrypt(plaintext, PASSWORD);
        assertThrows(InvalidPasswordException.class, () -> codec.decrypt(envelope, null));
        assertThrows(InvalidPasswordException.class, () -> codec.decrypt(envelope, "short"));
    }

    @Test
    void encryptsDifferentPlaintextToDifferentCiphertext() {
        EncryptedEnvelope first = EncryptedEnvelope.fromBytes(
                codec.encrypt("one".getBytes(StandardCharsets.UTF_8), PASSWORD));
        EncryptedEnvelope second = EncryptedEnvelope.fromBytes(
                codec.encrypt("two".getBytes(StandardCharsets.UTF_8), PASSWORD));

        assertFalse(Arrays.equals(first.ciphertext(), second.ciphertext()));
    }

}
