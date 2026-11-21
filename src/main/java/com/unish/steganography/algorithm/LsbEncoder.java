package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InsufficientCapacityException;
import com.unish.steganography.exception.InvalidImageException;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;

public final class LsbEncoder {

    public BufferedImage encode(BufferedImage cover, String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        return encodeBytes(cover, message.getBytes(StandardCharsets.UTF_8));
    }

    public BufferedImage encodeBytes(BufferedImage cover, byte[] payload) {
        if (cover == null) {
            throw new InvalidImageException("Cover image must not be null");
        }
        if (payload == null) {
            throw new IllegalArgumentException("Payload must not be null");
        }
        int width = cover.getWidth();
        int height = cover.getHeight();
        if (width <= 0 || height <= 0) {
            throw new InvalidImageException("Cover image has no pixels");
        }

        if (!LsbFormat.canHoldHeader(width, height)) {
            throw new InsufficientCapacityException(
                    "Image is too small to hold the " + LsbFormat.HEADER_BYTES + "-byte header");
        }
        int capacity = LsbFormat.maxPayloadBytes(width, height);
        if (payload.length > capacity) {
            throw new InsufficientCapacityException(
                    "Message requires " + payload.length + " bytes but image capacity is " + capacity + " bytes");
        }

        byte[] header = LsbFormat.createHeader(payload.length);
        byte[] data = new byte[LsbFormat.HEADER_BYTES + payload.length];
        System.arraycopy(header, 0, data, 0, header.length);
        System.arraycopy(payload, 0, data, LsbFormat.HEADER_BYTES, payload.length);

        BufferedImage stego = copy(cover);
        LsbBitStream.write(stego, data);
        return stego;
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                copy.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return copy;
    }

}
