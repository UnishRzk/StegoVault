package com.unish.steganography.algorithm;

import com.unish.steganography.util.ImageIoUtils;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

public final class LsbFileCodec {

    private final LsbEncoder encoder = new LsbEncoder();
    private final LsbDecoder decoder = new LsbDecoder();

    public void encode(Path source, String message, Path destination) {
        ImageIoUtils.requireDistinctFiles(source, destination);
        BufferedImage cover = ImageIoUtils.readLosslessImage(source);
        BufferedImage stego = encoder.encode(cover, message);
        ImageIoUtils.writeLosslessImage(stego, destination);
    }

    public String decode(Path source) {
        return decoder.decode(ImageIoUtils.readLosslessImage(source));
    }

}
