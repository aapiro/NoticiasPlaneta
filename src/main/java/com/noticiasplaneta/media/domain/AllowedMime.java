package com.noticiasplaneta.media.domain;

import java.util.Set;

/** Lista blanca de tipos aceptados. SVG queda fuera a propósito: XSS almacenado. */
public final class AllowedMime {

    public static final long MAX_SIZE_BYTES = 15L * 1024 * 1024;

    private static final Set<String> ALLOWED = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/avif", "image/gif");

    private AllowedMime() {
    }

    public static boolean isAllowed(String mime) {
        return mime != null && ALLOWED.contains(mime.toLowerCase());
    }

    public static String extensionFor(String mime) {
        return switch (mime.toLowerCase()) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            case "image/avif" -> "avif";
            case "image/gif" -> "gif";
            default -> throw new IllegalArgumentException("MIME no permitido: " + mime);
        };
    }

    /** Detecta el tipo real por magic bytes, sin creerse la extensión ni el Content-Type. */
    public static String detect(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "application/octet-stream";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "image/jpeg";
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return "image/png";
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') {
            return "image/gif";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        if (bytes[4] == 'f' && bytes[5] == 't' && bytes[6] == 'y' && bytes[7] == 'p') {
            return "image/avif";
        }
        return "application/octet-stream";
    }
}
