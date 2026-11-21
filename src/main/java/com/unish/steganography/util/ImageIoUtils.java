package com.unish.steganography.util;

import com.unish.steganography.exception.InvalidImageException;
import com.unish.steganography.exception.UnsafeOutputPathException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

public final class ImageIoUtils {

    public static final int MAX_DIMENSION = 8192;
    public static final long MAX_PIXELS = 16_777_216L;

    private static final Set<String> LOSSLESS_FORMATS = Set.of("png", "bmp");

    private ImageIoUtils() {
    }

    public static BufferedImage readLosslessImage(Path path) {
        if (path == null) {
            throw new InvalidImageException("Image path must not be null");
        }
        if (!Files.isRegularFile(path)) {
            throw new InvalidImageException("Image file does not exist: " + path);
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(path.toFile())) {
            return readLosslessImage(input, path.toString());
        } catch (IOException ex) {
            throw new InvalidImageException("Failed to read image: " + path, ex);
        }
    }

    public static BufferedImage readLosslessImage(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidImageException("Image file is empty");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            return readLosslessImage(input, "uploaded image");
        } catch (IOException ex) {
            throw new InvalidImageException("Failed to read uploaded image", ex);
        }
    }

    private static BufferedImage readLosslessImage(ImageInputStream input, String description) throws IOException {
        if (input == null) {
            throw new InvalidImageException("Unable to open image: " + description);
        }
        Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) {
            throw new InvalidImageException("Unsupported or unreadable image: " + description);
        }
        ImageReader reader = readers.next();
        try {
            String format = reader.getFormatName();
            if (format == null || !LOSSLESS_FORMATS.contains(format.toLowerCase(Locale.ROOT))) {
                throw new InvalidImageException(
                        "Unsupported image format '" + format + "'. Use PNG or BMP.");
            }
            reader.setInput(input, true, true);
            requireAcceptableDimensions(reader, description);
            BufferedImage image = reader.read(0);
            if (image == null) {
                throw new InvalidImageException("Image could not be decoded: " + description);
            }
            return toArgb(image);
        } finally {
            reader.dispose();
        }
    }

    private static void requireAcceptableDimensions(ImageReader reader, String description) throws IOException {
        int width;
        int height;
        try {
            width = reader.getWidth(0);
            height = reader.getHeight(0);
        } catch (RuntimeException ex) {
            throw new InvalidImageException("Image dimensions could not be read: " + description, ex);
        }
        if (width <= 0 || height <= 0) {
            throw new InvalidImageException("Image has invalid dimensions: " + description);
        }
        if (width > MAX_DIMENSION || height > MAX_DIMENSION || (long) width * height > MAX_PIXELS) {
            throw new InvalidImageException(
                    "Image dimensions exceed the allowed limit of " + MAX_DIMENSION + " pixels per side and "
                            + MAX_PIXELS + " total pixels");
        }
    }

    public static byte[] toPngBytes(BufferedImage image) {
        if (image == null) {
            throw new InvalidImageException("Image must not be null");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            if (!ImageIO.write(image, "png", output)) {
                throw new InvalidImageException("No PNG writer is available");
            }
        } catch (IOException ex) {
            throw new InvalidImageException("Failed to encode PNG", ex);
        }
        return output.toByteArray();
    }

    public static void writeLosslessImage(BufferedImage image, Path path) {
        if (image == null) {
            throw new InvalidImageException("Image must not be null");
        }
        if (path == null) {
            throw new InvalidImageException("Output path must not be null");
        }
        String format = formatFromPath(path);
        if (!LOSSLESS_FORMATS.contains(format)) {
            throw new InvalidImageException("Unsupported output format '" + format + "'. Use PNG or BMP.");
        }
        Path parent = path.toAbsolutePath().normalize().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            throw new InvalidImageException("Output directory does not exist: " + parent);
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(format);
        if (!writers.hasNext()) {
            throw new InvalidImageException("No ImageIO writer for format: " + format);
        }
        ImageWriter writer = writers.next();
        BufferedImage output = "bmp".equals(format) ? toRgb(image) : image;
        try (ImageOutputStream out = ImageIO.createImageOutputStream(path.toFile())) {
            if (out == null) {
                throw new InvalidImageException("Unable to open output: " + path);
            }
            writer.setOutput(out);
            writer.write(output);
        } catch (IOException ex) {
            throw new InvalidImageException("Failed to write image: " + path, ex);
        } finally {
            writer.dispose();
        }
    }

    public static void requireDistinctFiles(Path source, Path destination) {
        if (source == null || destination == null) {
            throw new InvalidImageException("Source and destination paths must not be null");
        }
        Path sourcePath = source.toAbsolutePath().normalize();
        Path destinationPath = destination.toAbsolutePath().normalize();
        if (sourcePath.equals(destinationPath)) {
            throw new UnsafeOutputPathException("Output path must differ from the source image: " + source);
        }
        try {
            if (Files.exists(destinationPath) && Files.isSameFile(sourcePath, destinationPath)) {
                throw new UnsafeOutputPathException("Output path resolves to the source image: " + source);
            }
        } catch (IOException ex) {
            throw new UnsafeOutputPathException("Unable to compare source and output paths", ex);
        }
    }

    private static String formatFromPath(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            throw new InvalidImageException("Output path must end in .png or .bmp");
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            throw new InvalidImageException("Output path must end in .png or .bmp");
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static BufferedImage toArgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                converted.setRGB(x, y, source.getRGB(x, y));
            }
        }
        return converted;
    }

    private static BufferedImage toRgb(BufferedImage source) {
        if (source.getType() == BufferedImage.TYPE_INT_RGB) {
            return source;
        }
        BufferedImage converted = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                converted.setRGB(x, y, source.getRGB(x, y) & 0xFFFFFF);
            }
        }
        return converted;
    }

}
