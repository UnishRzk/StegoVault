package com.unish.steganography.algorithm;

import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.exception.InvalidPayloadException;
import com.unish.steganography.util.Utf8Utils;

import java.awt.image.BufferedImage;
import java.util.Arrays;

public final class LsbDecoder {

    public String decode(BufferedImage stego) {
        return Utf8Utils.decode(decodeBytes(stego));
    }

    public byte[] decodeBytes(BufferedImage stego) {
        if (stego == null) {
            throw new InvalidImageException("Stego image must not be null");
        }
        int width = stego.getWidth();
        int height = stego.getHeight();
        if (width <= 0 || height <= 0) {
            throw new InvalidImageException("Stego image has no pixels");
        }
        if (!LsbFormat.canHoldHeader(width, height)) {
            throw new InvalidPayloadException("Image is too small to contain a payload header");
        }

        byte[] header = LsbBitStream.read(stego, LsbFormat.HEADER_BYTES);
        int payloadLength = LsbFormat.readPayloadLength(header);
        int capacity = LsbFormat.maxPayloadBytes(width, height);
        if (payloadLength > capacity) {
            throw new InvalidPayloadException(
                    "Declared payload length " + payloadLength + " exceeds image capacity " + capacity);
        }

        byte[] data = LsbBitStream.read(stego, LsbFormat.HEADER_BYTES + payloadLength);
        return Arrays.copyOfRange(data, LsbFormat.HEADER_BYTES, data.length);
    }

}
