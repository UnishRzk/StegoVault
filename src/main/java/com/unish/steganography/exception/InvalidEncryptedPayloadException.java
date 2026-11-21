package com.unish.steganography.exception;

public class InvalidEncryptedPayloadException extends SteganographyException {

    public InvalidEncryptedPayloadException(String message) {
        super(message);
    }

    public InvalidEncryptedPayloadException(String message, Throwable cause) {
        super(message, cause);
    }

}
