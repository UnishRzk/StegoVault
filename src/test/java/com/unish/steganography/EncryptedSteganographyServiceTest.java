package com.unish.steganography;

import com.unish.steganography.algorithm.LsbDecoder;
import com.unish.steganography.algorithm.LsbEncoder;
import com.unish.steganography.exception.InsufficientCapacityException;
import com.unish.steganography.exception.InvalidEncryptedPayloadException;
import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.exception.InvalidPayloadException;
import com.unish.steganography.util.ImageIoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EncryptedSteganographyServiceTest {

    private static final String PASSWORD = "strong-passphrase";
    private static final String DECRYPTION_FAILED =
            "Unable to decrypt payload: incorrect password or corrupted data";

    private final SteganographyService service = new SteganographyService();
    private final LsbEncoder encoder = new LsbEncoder();
    private final LsbDecoder decoder = new LsbDecoder();

    @Test
    void encodesAndDecodesEncryptedPng() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", imageBytes("png", cover(64, 64)));

        byte[] stego = service.encodeEncrypted(cover, "hidden and encrypted", PASSWORD);

        assertEquals("hidden and encrypted",
                service.decodeEncrypted(imagePart("stego.png", stego), PASSWORD));
    }

    @Test
    void roundTripsEmptyPlaintext() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", imageBytes("png", cover(64, 64)));

        byte[] stego = service.encodeEncrypted(cover, "", PASSWORD);

        assertEquals("", service.decodeEncrypted(imagePart("stego.png", stego), PASSWORD));
    }

    @Test
    void rejectsWrongPasswordWithoutReturningPlaintext() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", imageBytes("png", cover(64, 64)));
        byte[] stego = service.encodeEncrypted(cover, "hidden and encrypted", PASSWORD);

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> service.decodeEncrypted(imagePart("stego.png", stego), "wrong-passphrase"));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
    }

    @Test
    void rejectsTamperedEmbeddedCiphertext() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", imageBytes("png", cover(64, 64)));
        byte[] stego = service.encodeEncrypted(cover, "hidden and encrypted", PASSWORD);

        BufferedImage stegoImage = ImageIoUtils.readLosslessImage(stego);
        byte[] envelope = decoder.decodeBytes(stegoImage);
        envelope[envelope.length - 1] ^= 0x01;
        byte[] tampered = ImageIoUtils.toPngBytes(encoder.encodeBytes(stegoImage, envelope));

        InvalidEncryptedPayloadException ex = assertThrows(InvalidEncryptedPayloadException.class,
                () -> service.decodeEncrypted(imagePart("tampered.png", tampered), PASSWORD));

        assertEquals(DECRYPTION_FAILED, ex.getMessage());
    }

    @Test
    void rejectsMalformedEmbeddedEnvelope() throws IOException {
        MockMultipartFile cover = imagePart("cover.png", imageBytes("png", cover(64, 64)));
        BufferedImage coverImage = ImageIoUtils.readLosslessImage(cover.getBytes());
        byte[] malformed = "not-an-encrypted-envelope".getBytes(StandardCharsets.UTF_8);
        byte[] stego = ImageIoUtils.toPngBytes(encoder.encodeBytes(coverImage, malformed));

        assertThrows(InvalidEncryptedPayloadException.class,
                () -> service.decodeEncrypted(imagePart("malformed.png", stego), PASSWORD));
    }

    @Test
    void distinguishesMissingLsbPayloadFromMalformedEnvelope() throws IOException {
        MockMultipartFile plain = imagePart("plain.png", imageBytes("png", cover(64, 64)));

        assertThrows(InvalidPayloadException.class,
                () -> service.decodeEncrypted(plain, PASSWORD));
    }

    @Test
    void rejectsInsufficientImageCapacity() throws IOException {
        MockMultipartFile tiny = imagePart("tiny.png", imageBytes("png", solidCover(4, 5)));

        assertThrows(InsufficientCapacityException.class,
                () -> service.encodeEncrypted(tiny, "x", PASSWORD));
    }

    @Test
    void rejectsOversizedImageDimensions() throws IOException {
        MockMultipartFile huge = imagePart("huge.png",
                imageBytes("png", solidCover(ImageIoUtils.MAX_DIMENSION + 1, 1)));

        assertThrows(InvalidImageException.class,
                () -> service.encodeEncrypted(huge, "x", PASSWORD));
    }

    @Test
    void rejectsOversizedUploadBeforeDecoding() {
        MockMultipartFile tooLarge = imagePart("large.png", new byte[5 * 1024 * 1024 + 1]);

        assertThrows(InvalidImageException.class,
                () -> service.encodeEncrypted(tooLarge, "x", PASSWORD));
    }

    private static MockMultipartFile imagePart(String name, byte[] bytes) {
        return new MockMultipartFile("image", name, "image/png", bytes);
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
