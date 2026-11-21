package com.unish.steganography;

import com.unish.steganography.algorithm.LsbDecoder;
import com.unish.steganography.algorithm.LsbEncoder;
import com.unish.steganography.crypto.AesGcmCodec;
import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.util.ImageIoUtils;
import com.unish.steganography.util.Utf8Utils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Service
public class SteganographyService {

    private static final long MAX_UPLOAD_BYTES = 5L * 1024 * 1024;

    private final LsbEncoder encoder = new LsbEncoder();
    private final LsbDecoder decoder = new LsbDecoder();
    private final AesGcmCodec crypto = new AesGcmCodec();

    public byte[] encode(MultipartFile image, String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        BufferedImage cover = readUpload(image);
        BufferedImage stego = encoder.encode(cover, message);
        return ImageIoUtils.toPngBytes(stego);
    }

    public String decode(MultipartFile image) {
        BufferedImage stego = readUpload(image);
        return decoder.decode(stego);
    }

    public byte[] encodeEncrypted(MultipartFile image, String message, String password) {
        if (message == null) {
            throw new IllegalArgumentException("Message must not be null");
        }
        BufferedImage cover = readUpload(image);
        byte[] envelope = crypto.encrypt(message.getBytes(StandardCharsets.UTF_8), password);
        BufferedImage stego = encoder.encodeBytes(cover, envelope);
        return ImageIoUtils.toPngBytes(stego);
    }

    public String decodeEncrypted(MultipartFile image, String password) {
        BufferedImage stego = readUpload(image);
        byte[] envelope = decoder.decodeBytes(stego);
        byte[] plaintext = crypto.decrypt(envelope, password);
        return Utf8Utils.decode(plaintext);
    }

    private static BufferedImage readUpload(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            throw new InvalidImageException("Image file is required and must not be empty");
        }
        if (image.getSize() > MAX_UPLOAD_BYTES) {
            throw new InvalidImageException("Uploaded image exceeds the maximum allowed size");
        }
        try {
            return ImageIoUtils.readLosslessImage(image.getBytes());
        } catch (IOException ex) {
            throw new InvalidImageException("Unable to read uploaded image", ex);
        }
    }

}
