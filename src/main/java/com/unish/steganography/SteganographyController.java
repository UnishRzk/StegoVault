package com.unish.steganography;

import com.unish.steganography.dto.DecodeResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/steganography")
public class SteganographyController {

    private final SteganographyService service;

    public SteganographyController(SteganographyService service) {
        this.service = service;
    }

    @PostMapping(value = "/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> encode(
            @RequestPart("image") MultipartFile image,
            @RequestParam("message") String message) {
        byte[] png = service.encode(image, message);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("stego.png")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(png);
    }

    @PostMapping(value = "/decode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DecodeResponse decode(@RequestPart("image") MultipartFile image) {
        return new DecodeResponse(service.decode(image));
    }

    @PostMapping(value = "/encrypted/encode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> encodeEncrypted(
            @RequestPart("image") MultipartFile image,
            @RequestParam("message") String message,
            @RequestParam("password") String password) {
        byte[] png = service.encodeEncrypted(image, message, password);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename("stego-encrypted.png")
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(png);
    }

    @PostMapping(value = "/encrypted/decode", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DecodeResponse decodeEncrypted(
            @RequestPart("image") MultipartFile image,
            @RequestParam("password") String password) {
        return new DecodeResponse(service.decodeEncrypted(image, password));
    }

}
