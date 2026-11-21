package com.unish.steganography.exception;

public class InvalidImageException extends SteganographyException {

    public InvalidImageException(String message) {
        super(message);
    }

    public InvalidImageException(String message, Throwable cause) {
        super(message, cause);
    }

}
