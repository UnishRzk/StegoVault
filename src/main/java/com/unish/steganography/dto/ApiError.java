package com.unish.steganography.dto;

public record ApiError(String timestamp, int status, String error, String message, String path) {
}
