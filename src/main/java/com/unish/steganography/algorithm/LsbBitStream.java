package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InvalidPayloadException;

import java.awt.image.BufferedImage;

final class LsbBitStream {

    private static final int[] CHANNEL_SHIFTS = {16, 8, 0};

    private LsbBitStream() {
    }

    static void write(BufferedImage image, byte[] data) {
        int width = image.getWidth();
        int height = image.getHeight();
        int totalBits = data.length * 8;
        int bitIndex = 0;

        for (int y = 0; y < height && bitIndex < totalBits; y++) {
            for (int x = 0; x < width && bitIndex < totalBits; x++) {
                int rgb = image.getRGB(x, y);
                for (int shift : CHANNEL_SHIFTS) {
                    if (bitIndex >= totalBits) {
                        break;
                    }
                    int bit = (data[bitIndex >>> 3] >>> (7 - (bitIndex & 7))) & 1;
                    int channel = (rgb >>> shift) & 0xFF;
                    channel = (channel & 0xFE) | bit;
                    rgb = (rgb & ~(0xFF << shift)) | (channel << shift);
                    bitIndex++;
                }
                image.setRGB(x, y, rgb);
            }
        }
    }

    static byte[] read(BufferedImage image, int byteCount) {
        if (byteCount < 0) {
            throw new InvalidPayloadException("Payload length is negative");
        }
        long requiredBits = (long) byteCount * 8;
        if (requiredBits > LsbFormat.availableBits(image.getWidth(), image.getHeight())) {
            throw new InvalidPayloadException("Image does not contain enough data for the declared payload");
        }

        byte[] data = new byte[byteCount];
        int totalBits = byteCount * 8;
        int bitIndex = 0;

        outer:
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgb = image.getRGB(x, y);
                for (int shift : CHANNEL_SHIFTS) {
                    if (bitIndex >= totalBits) {
                        break outer;
                    }
                    int bit = (rgb >>> shift) & 1;
                    if (bit == 1) {
                        data[bitIndex >>> 3] |= (byte) (1 << (7 - (bitIndex & 7)));
                    }
                    bitIndex++;
                }
            }
        }
        return data;
    }

}
