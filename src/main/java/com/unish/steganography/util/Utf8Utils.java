package com.unish.steganography.util;

import com.unish.steganography.exception.InvalidPayloadException;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class Utf8Utils {

    private Utf8Utils() {
    }

    public static String decode(byte[] payload) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(payload)).toString();
        } catch (CharacterCodingException ex) {
            throw new InvalidPayloadException("Payload is not valid UTF-8", ex);
        }
    }

}
