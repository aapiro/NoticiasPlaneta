package com.noticiasplaneta.ingest.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Huella del contenido normalizado (título + cuerpo).
 *
 * <p>RN2 (F01): el mismo texto publicado por dos medios son dos fuentes distintas; la huella
 * permite detectarlo y agrupar, no descartar.
 */
public record ContentHash(String value) {

    public ContentHash {
        if (value == null || value.length() != 64) {
            throw new DomainException("CONTENT_HASH_INVALID", "La huella debe ser un sha256 hexadecimal");
        }
    }

    public static ContentHash of(String title, String body) {
        String normalized = normalize(title) + "\u0000" + normalize(body);
        return new ContentHash(sha256Hex(normalized));
    }

    /** Minúsculas, sin acentos, espacios colapsados. */
    static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static String sha256Hex(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
