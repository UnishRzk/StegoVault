package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.exception.UnsafeOutputPathException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LsbFileCodecTest {

    @TempDir
    Path tempDir;

    private final LsbFileCodec codec = new LsbFileCodec();

    @Test
    void roundTripsPngWithoutChangingSource() throws IOException {
        Path source = tempDir.resolve("cover.png");
        Path output = tempDir.resolve("stego.png");
        writeImage(source, "png", opaqueCover(64, 64));
        byte[] original = Files.readAllBytes(source);

        codec.encode(source, "file round trip", output);

        assertArrayEquals(original, Files.readAllBytes(source));
        assertEquals("file round trip", codec.decode(output));
    }

    @Test
    void roundTripsBmp() throws IOException {
        Path source = tempDir.resolve("cover.bmp");
        Path output = tempDir.resolve("stego.bmp");
        writeImage(source, "bmp", opaqueCover(64, 64));

        codec.encode(source, "bmp round trip", output);

        assertEquals("bmp round trip", codec.decode(output));
    }

    @Test
    void rejectsDestinationThatIsTheSource() throws IOException {
        Path source = tempDir.resolve("cover.png");
        writeImage(source, "png", opaqueCover(32, 32));

        assertThrows(UnsafeOutputPathException.class, () -> codec.encode(source, "x", source));
    }

    @Test
    void rejectsDestinationThatResolvesToSource() throws IOException {
        Path source = tempDir.resolve("cover.png");
        writeImage(source, "png", opaqueCover(32, 32));
        Path sameFile = tempDir.resolve(".").resolve("cover.png");

        assertThrows(UnsafeOutputPathException.class, () -> codec.encode(source, "x", sameFile));
    }

    @Test
    void rejectsLossyInputImage() throws IOException {
        Path source = tempDir.resolve("cover.jpg");
        writeImage(source, "jpg", opaqueCover(32, 32));

        assertThrows(InvalidImageException.class, () -> codec.decode(source));
    }

    @Test
    void rejectsUnsupportedOutputExtension() throws IOException {
        Path source = tempDir.resolve("cover.png");
        writeImage(source, "png", opaqueCover(32, 32));

        assertThrows(InvalidImageException.class, () -> codec.encode(source, "x", tempDir.resolve("stego.jpg")));
    }

    @Test
    void rejectsMissingSourceImage() {
        assertThrows(InvalidImageException.class, () -> codec.decode(tempDir.resolve("missing.png")));
    }

    private static void writeImage(Path path, String format, BufferedImage image) throws IOException {
        assertTrue(ImageIO.write(image, format, path.toFile()));
    }

    private static BufferedImage opaqueCover(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 13 + y * 7) & 0xFFFFFF);
            }
        }
        return image;
    }

}
