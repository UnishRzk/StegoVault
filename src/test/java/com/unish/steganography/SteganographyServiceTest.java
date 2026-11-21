package com.unish.steganography;

import com.unish.steganography.exception.InsufficientCapacityException;
import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.exception.InvalidPayloadException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SteganographyServiceTest {

    private final SteganographyService service = new SteganographyService();

    @Test
    void encodesAndDecodesPng() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", "image/png", imageBytes("png", cover(64, 64)));

        byte[] stego = service.encode(cover, "hello service");

        assertEquals("hello service", service.decode(imagePart("stego.png", "image/png", stego)));
    }

    @Test
    void encodesBmpInput() throws IOException {
        MockMultipartFile cover = imagePart("cover.bmp", "image/bmp", imageBytes("bmp", cover(64, 64)));

        byte[] stego = service.encode(cover, "bmp input");

        assertEquals("bmp input", service.decode(imagePart("stego.png", "image/png", stego)));
    }

    @Test
    void supportsUnicodeAndEmptyMessages() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", "image/png", imageBytes("png", cover(64, 64)));

        byte[] unicode = service.encode(cover, "नमस्ते 🌍");
        assertEquals("नमस्ते 🌍", service.decode(imagePart("stego.png", "image/png", unicode)));

        byte[] empty = service.encode(cover, "");
        assertEquals("", service.decode(imagePart("stego.png", "image/png", empty)));
    }

    @Test
    void rejectsEmptyUpload() {
        MockMultipartFile empty = imagePart("empty.png", "image/png", new byte[0]);

        assertThrows(InvalidImageException.class, () -> service.decode(empty));
    }

    @Test
    void rejectsUnsupportedFormat() throws IOException {
        MockMultipartFile jpeg = imagePart("cover.jpg", "image/jpeg", imageBytes("jpg", cover(32, 32)));

        assertThrows(InvalidImageException.class, () -> service.decode(jpeg));
    }

    @Test
    void rejectsImageWithoutPayload() throws IOException {
        MockMultipartFile plain = imagePart("plain.png", "image/png", imageBytes("png", solidCover(16, 16)));

        assertThrows(InvalidPayloadException.class, () -> service.decode(plain));
    }

    @Test
    void rejectsInsufficientCapacity() throws IOException {
        MockMultipartFile tiny = imagePart("tiny.png", "image/png", imageBytes("png", solidCover(4, 5)));

        assertThrows(InsufficientCapacityException.class, () -> service.encode(tiny, "x"));
    }

    @Test
    void ignoresFilenameAndContentType() throws IOException {
        MockMultipartFile spoofed = imagePart("notes.txt", "text/plain", imageBytes("png", cover(64, 64)));

        byte[] stego = service.encode(spoofed, "content based");

        assertEquals("content based",
                service.decode(imagePart("whatever.bin", "application/octet-stream", stego)));
    }

    private static MockMultipartFile imagePart(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("image", name, contentType, bytes);
    }

    private static byte[] imageBytes(String format, BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) {
            throw new IOException("No writer for format " + format);
        }
        return output.toByteArray();
    }

    private static BufferedImage cover(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 13 + y * 7) & 0xFFFFFF);
            }
        }
        return image;
    }

    private static BufferedImage solidCover(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

}
