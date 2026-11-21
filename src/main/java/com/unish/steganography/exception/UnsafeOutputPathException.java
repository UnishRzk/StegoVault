package com.unish.steganography.exception;

public class UnsafeOutputPathException extends SteganographyException {

    public UnsafeOutputPathException(String message) {
        super(message);
    }

    public UnsafeOutputPathException(String message, Throwable cause) {
        super(message, cause);
    }

}
