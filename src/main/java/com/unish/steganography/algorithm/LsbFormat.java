package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InvalidPayloadException;

public final class LsbFormat {

    public static final byte[] MAGIC = {'S', 'T', 'E', 'G'};
    public static final int HEADER_BYTES = 8;
    public static final int HEADER_BITS = HEADER_BYTES * 8;
    public static final int CHANNELS_PER_PIXEL = 3;

    private LsbFormat() {
    }

    public static long availableBits(int width, int height) {
        if (width <= 0 || height <= 0) {
            return 0;
        }
        return (long) width * height * CHANNELS_PER_PIXEL;
    }

    public static boolean canHoldHeader(int width, int height) {
        return availableBits(width, height) >= HEADER_BITS;
    }

    public static int maxPayloadBytes(int width, int height) {
        long available = availableBits(width, height) - HEADER_BITS;
        if (available < 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, available / 8);
    }

    public static byte[] createHeader(int payloadLength) {
        if (payloadLength < 0) {
            throw new IllegalArgumentException("Payload length must not be negative");
        }
        byte[] header = new byte[HEADER_BYTES];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[4] = (byte) (payloadLength >>> 24);
        header[5] = (byte) (payloadLength >>> 16);
        header[6] = (byte) (payloadLength >>> 8);
        header[7] = (byte) payloadLength;
        return header;
    }

    public static int readPayloadLength(byte[] header) {
        if (header == null || header.length < HEADER_BYTES) {
            throw new InvalidPayloadException("Payload header is missing or incomplete");
        }
        for (int i = 0; i < MAGIC.length; i++) {
            if (header[i] != MAGIC[i]) {
                throw new InvalidPayloadException("Image does not contain a steganography payload");
            }
        }
        int length = ((header[4] & 0xFF) << 24)
                | ((header[5] & 0xFF) << 16)
                | ((header[6] & 0xFF) << 8)
                | (header[7] & 0xFF);
        if (length < 0) {
            throw new InvalidPayloadException("Payload length is negative");
        }
        return length;
    }

}
