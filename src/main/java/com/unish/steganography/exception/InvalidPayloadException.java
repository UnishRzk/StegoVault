package com.unish.steganography.exception;

public class InvalidPayloadException extends SteganographyException {

    public InvalidPayloadException(String message) {
        super(message);
    }

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }

}
