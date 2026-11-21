package com.unish.steganography;

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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SteganographyController.class)
@Import(SteganographyService.class)
class SteganographyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SteganographyService service;

    @Test
    void encodesAndDecodesRoundTrip() throws Exception {
        byte[] cover = imageBytes("png", cover(64, 64));

        MvcResult result = mockMvc.perform(multipart("/api/steganography/encode")
                        .file(imagePart("cover.png", "image/png", cover))
                        .param("message", "hello"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("stego.png")))
                .andReturn();

        byte[] stego = result.getResponse().getContentAsByteArray();
        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("stego.png", "image/png", stego)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("hello"));
    }

    @Test
    void decodesHiddenMessage() throws Exception {
        byte[] stego = service.encode(
                imagePart("cover.png", "image/png", imageBytes("png", cover(64, 64))),
                "secret");

        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("stego.png", "image/png", stego)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("secret"));
    }

    @Test
    void rejectsMissingImage() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encode")
                        .param("message", "hello"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.path").value("/api/steganography/encode"));
    }

    @Test
    void rejectsMissingMessage() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encode")
                        .file(imagePart("cover.png", "image/png", imageBytes("png", cover(32, 32)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsEmptyImage() throws Exception {
        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("empty.png", "image/png", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsUnsupportedImageFormat() throws Exception {
        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("cover.jpg", "image/jpeg", imageBytes("jpg", cover(32, 32)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsImageWithoutPayload() throws Exception {
        mockMvc.perform(multipart("/api/steganography/decode")
                        .file(imagePart("plain.png", "image/png", imageBytes("png", solidCover(16, 16)))))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));
    }

    @Test
    void rejectsInsufficientCapacity() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encode")
                        .file(imagePart("tiny.png", "image/png", imageBytes("png", solidCover(4, 5))))
                        .param("message", "x"))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void ignoresFilenameAndContentType() throws Exception {
        mockMvc.perform(multipart("/api/steganography/encode")
                        .file(imagePart("not-an-image.txt", "text/plain", imageBytes("png", cover(32, 32))))
                        .param("message", "ok"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));
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
