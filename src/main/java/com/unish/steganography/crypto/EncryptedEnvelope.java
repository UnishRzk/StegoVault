package com.unish.steganography.crypto;

import com.unish.steganography.exception.InvalidEncryptedPayloadException;

import java.nio.ByteBuffer;
import java.util.Arrays;

public final class EncryptedEnvelope {

    public static final byte[] MAGIC = {'S', 'G', 'C', 'E'};
    public static final int VERSION = 1;
    public static final int SALT_BYTES = 16;
    public static final int NONCE_BYTES = 12;
    public static final int TAG_BYTES = 16;
    public static final int MIN_ITERATIONS = 100_000;
    public static final int MAX_ITERATIONS = 2_000_000;

    private static final int FIXED_HEADER_BYTES = MAGIC.length
            + 1
            + Integer.BYTES
            + 1 + SALT_BYTES
            + 1 + NONCE_BYTES
            + 1
            + Integer.BYTES;

    private final int iterations;
    private final byte[] salt;
    private final byte[] nonce;
    private final byte[] ciphertext;

    public EncryptedEnvelope(int iterations, byte[] salt, byte[] nonce, byte[] ciphertext) {
        if (iterations < MIN_ITERATIONS || iterations > MAX_ITERATIONS) {
            throw new InvalidEncryptedPayloadException("Encrypted payload envelope has invalid parameters");
        }
        if (salt == null || salt.length != SALT_BYTES) {
            throw new InvalidEncryptedPayloadException("Encrypted payload envelope has invalid parameters");
        }
        if (nonce == null || nonce.length != NONCE_BYTES) {
            throw new InvalidEncryptedPayloadException("Encrypted payload envelope has invalid parameters");
        }
        if (ciphertext == null || ciphertext.length < TAG_BYTES) {
            throw new InvalidEncryptedPayloadException("Encrypted payload envelope has invalid parameters");
        }
        this.iterations = iterations;
        this.salt = salt.clone();
        this.nonce = nonce.clone();
        this.ciphertext = ciphertext.clone();
    }

    public int iterations() {
        return iterations;
    }

    public byte[] salt() {
        return salt.clone();
    }

    public byte[] nonce() {
        return nonce.clone();
    }

    public byte[] ciphertext() {
        return ciphertext.clone();
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(FIXED_HEADER_BYTES + ciphertext.length);
        buffer.put(MAGIC);
        buffer.put((byte) VERSION);
        buffer.putInt(iterations);
        buffer.put((byte) SALT_BYTES);
        buffer.put(salt);
        buffer.put((byte) NONCE_BYTES);
        buffer.put(nonce);
        buffer.put((byte) TAG_BYTES);
        buffer.putInt(ciphertext.length);
        buffer.put(ciphertext);
        return buffer.array();
    }

    public static EncryptedEnvelope fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length < FIXED_HEADER_BYTES) {
            throw malformed();
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);

        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!Arrays.equals(magic, MAGIC)) {
            throw malformed();
        }

        int version = Byte.toUnsignedInt(buffer.get());
        if (version != VERSION) {
            throw new InvalidEncryptedPayloadException("Unsupported encrypted payload version");
        }

        int iterations = buffer.getInt();

        int saltLength = Byte.toUnsignedInt(buffer.get());
        if (saltLength != SALT_BYTES || buffer.remaining() < saltLength) {
            throw malformed();
        }
        byte[] salt = new byte[saltLength];
        buffer.get(salt);

        int nonceLength = Byte.toUnsignedInt(buffer.get());
        if (nonceLength != NONCE_BYTES || buffer.remaining() < nonceLength) {
            throw malformed();
        }
        byte[] nonce = new byte[nonceLength];
        buffer.get(nonce);

        int tagLength = Byte.toUnsignedInt(buffer.get());
        if (tagLength != TAG_BYTES) {
            throw malformed();
        }

        int ciphertextLength = buffer.getInt();
        if (ciphertextLength < TAG_BYTES || ciphertextLength != buffer.remaining()) {
            throw malformed();
        }
        byte[] ciphertext = new byte[ciphertextLength];
        buffer.get(ciphertext);

        return new EncryptedEnvelope(iterations, salt, nonce, ciphertext);
    }

    private static InvalidEncryptedPayloadException malformed() {
        return new InvalidEncryptedPayloadException("Encrypted payload envelope is malformed");
    }

}
