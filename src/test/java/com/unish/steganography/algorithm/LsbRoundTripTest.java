package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InvalidPayloadException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LsbRoundTripTest {

    private final LsbEncoder encoder = new LsbEncoder();
    private final LsbDecoder decoder = new LsbDecoder();

    @Test
    void roundTripsAsciiMessage() {
        BufferedImage cover = cover(64, 64);
        String message = "Hello, steganography!";

        BufferedImage stego = encoder.encode(cover, message);

        assertEquals(message, decoder.decode(stego));
    }

    @Test
    void roundTripsUnicodeMessage() {
        BufferedImage cover = cover(64, 64);
        String message = "नमस्ते 🌍 — Steganography";

        BufferedImage stego = encoder.encode(cover, message);

        assertEquals(message, decoder.decode(stego));
    }

    @Test
    void roundTripsEmptyMessage() {
        BufferedImage cover = cover(16, 16);

        BufferedImage stego = encoder.encode(cover, "");

        assertEquals("", decoder.decode(stego));
    }

    @Test
    void doesNotMutateCoverImage() {
        BufferedImage cover = cover(32, 32);
        int[] before = pixels(cover);

        encoder.encode(cover, "unchanged");

        assertArrayEquals(before, pixels(cover));
    }

    @Test
    void rejectsImageWithoutMagicHeader() {
        BufferedImage image = cover(16, 16);

        assertThrows(InvalidPayloadException.class, () -> decoder.decode(image));
    }

    @Test
    void rejectsDeclaredLengthBeyondCapacity() {
        BufferedImage image = cover(16, 16);
        int capacity = LsbFormat.maxPayloadBytes(16, 16);
        LsbBitStream.write(image, LsbFormat.createHeader(capacity + 1));

        assertThrows(InvalidPayloadException.class, () -> decoder.decode(image));
    }

    @Test
    void rejectsInvalidUtf8Payload() {
        BufferedImage image = cover(16, 16);
        byte[] data = new byte[LsbFormat.HEADER_BYTES + 1];
        System.arraycopy(LsbFormat.createHeader(1), 0, data, 0, LsbFormat.HEADER_BYTES);
        data[LsbFormat.HEADER_BYTES] = (byte) 0xFF;
        LsbBitStream.write(image, data);

        assertThrows(InvalidPayloadException.class, () -> decoder.decode(image));
    }

    @Test
    void rejectsCorruptedMagicHeader() {
        BufferedImage image = cover(16, 16);
        byte[] header = LsbFormat.createHeader(0);
        header[0] = 0;
        LsbBitStream.write(image, header);

        assertThrows(InvalidPayloadException.class, () -> decoder.decode(image));
    }

    private static BufferedImage cover(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Random random = new Random(42);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        int[] pixels = new int[image.getWidth() * image.getHeight()];
        int index = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                pixels[index++] = image.getRGB(x, y);
            }
        }
        return pixels;
    }

}
