package com.unish.steganography;

import com.unish.steganography.algorithm.LsbDecoder;
import com.unish.steganography.algorithm.LsbEncoder;
import com.unish.steganography.util.ImageIoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SteganographyController.class)
@Import(SteganographyService.class)
class EncryptedSteganographyControllerTest {

    private static final String PASSWORD = "strong-passphrase";
    private static final String DECRYPTION_FAILED =
            "Unable to decrypt payload: incorrect password or corrupted data";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SteganographyService service;

    @Test
    void encodesAndDecodesEncryptedRoundTrip() throws Exception {
        byte[] cover = imageBytes("png", cover(64, 64));

        MvcResult result = mockMvc.perform(multipart("/api/steganography/encrypted/encode")
                        .file(imagePart("cover.png", "image/png", cover))
                        .param("message", "hello encrypted")
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("stego-encrypted.png")))
                .andReturn();

        byte[] stego = result.getResponse().getContentAsByteArray();
        mockMvc.perform(multipart("/api/steganography/encrypted/decode")
                        .file(imagePart("stego.png", "image/png", stego))
                        .param("password", PASSWORD))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("hello encrypted"));
    }

    @Test
    void rejectsWrongPasswordWithGenericError() throws Exception {
        byte[] stego = service.encodeEncrypted(
                imagePart("cover.png", "image/png", imageBytes("png", cover(64, 64))),
                "hello encrypted",
                PASSWORD);

        mockMvc.perform(multipart("/api/steganography/encrypted/decode")
                        .file(imagePart("stego.png", "image/png", stego))
                        .param("password", "wrong-passphrase"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(DECRYPTION_FAILED));
    }

    @Test
    void rejectsTamperedCiphertextWithSameGenericError() throws Exception {
        byte[] stego = service.encodeEncrypted(
                imagePart("cover.png", "image/png", imageBytes("png", cover(64, 64))),
                "hello encrypted",
                PASSWORD);

        BufferedImage stegoImage = ImageIoUtils.readLosslessImage(stego);
        LsbDecoder decoder = new LsbDecoder();
        byte[] envelope = decoder.decodeBytes(stegoImage);
        envelope[envelope.length - 1] ^= 0x01;
        byte[] tampered = ImageIoUtils.toPngBytes(new LsbEncoder().encodeBytes(stegoImage, envelope));

        mockMvc.perform(multipart("/api/steganography/encrypted/decode")
                        .file(imagePart("stego.png", "image/png", tampered))
                        .param("password", PASSWORD))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.message").value(DECRYPTION_FAILED));
    }

    @Test
    void rejectsMalformedEncryptedEnvelope() throws Exception {
        BufferedImage coverImage = ImageIoUtils.readLosslessImage(imageBytes("png", cover(64, 64)));
        byte[] malformed = "not-an-encrypted-envelope".getBytes(StandardCharsets.UTF_8);
        byte[] stego = ImageIoUtils.toPngBytes(new LsbEncoder().encodeBytes(coverImage, malformed));

        mockMvc.perform(multipart("/api/steganography/encrypted/decode")
                        .file(imagePart("malformed.png", "image/png", stego))
                        .param("password", PASSWORD))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void rejectsMissingPassword() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encrypted/encode")
                        .file(imagePart("cover.png", "image/png", imageBytes("png", cover(32, 32))))
                        .param("message", "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsShortPassword() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encrypted/encode")
                        .file(imagePart("cover.png", "image/png", imageBytes("png", cover(32, 32))))
                        .param("message", "hello")
                        .param("password", "short"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsInsufficientCapacity() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encrypted/encode")
                        .file(imagePart("tiny.png", "image/png", imageBytes("png", solidCover(4, 5))))
                        .param("message", "x")
                        .param("password", PASSWORD))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void keepsUnencryptedEndpointsWorking() throws Exception {
        byte[] cover = imageBytes("png", cover(64, 64));

        MvcResult result = mockMvc.perform(multipart("/api/steganography/encode")
                        .file(imagePart("cover.png", "image/png", cover))
                        .param("message", "plain"))
                .andExpect(status().isOk())
                .andReturn();

        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("stego.png", "image/png",
                                result.getResponse().getContentAsByteArray())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("plain"));
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
