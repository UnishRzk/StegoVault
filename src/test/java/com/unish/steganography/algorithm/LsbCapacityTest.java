package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InsufficientCapacityException;
import com.unish.steganography.exception.InvalidPayloadException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LsbCapacityTest {

    private final LsbEncoder encoder = new LsbEncoder();
    private final LsbDecoder decoder = new LsbDecoder();

    @Test
    void computesCapacityFromRgbChannelsAndHeader() {
        assertEquals((10 * 10 * 3 - 64) / 8, LsbFormat.maxPayloadBytes(10, 10));
        assertEquals(0, LsbFormat.maxPayloadBytes(1, 1));
    }

    @Test
    void acceptsMessageAtExactCapacity() {
        int width = 10;
        int height = 10;
        int capacity = LsbFormat.maxPayloadBytes(width, height);
        String message = "a".repeat(capacity);

        BufferedImage stego = encoder.encode(cover(width, height), message);

        assertEquals(message, decoder.decode(stego));
    }

    @Test
    void rejectsMessageOneByteOverCapacity() {
        int width = 10;
        int height = 10;
        int capacity = LsbFormat.maxPayloadBytes(width, height);
        String message = "a".repeat(capacity + 1);

        assertThrows(InsufficientCapacityException.class, () -> encoder.encode(cover(width, height), message));
    }

    @Test
    void rejectsImageTooSmallForHeader() {
        BufferedImage image = cover(4, 5);

        assertThrows(InsufficientCapacityException.class, () -> encoder.encode(image, ""));
        assertThrows(InvalidPayloadException.class, () -> decoder.decode(image));
    }

    @Test
    void allowsEmptyMessageWhenHeaderFits() {
        BufferedImage stego = encoder.encode(cover(5, 5), "");

        assertEquals("", decoder.decode(stego));
    }

    private static BufferedImage cover(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, 0xFF000000 | (x * 31 + y * 17));
            }
        }
        return image;
    }

}
