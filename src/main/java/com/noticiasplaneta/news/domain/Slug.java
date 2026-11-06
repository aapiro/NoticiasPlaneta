package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Slug canónico. ADR-001: inmutable tras la primera publicación.
 */
public record Slug(String value) {

    private static final Pattern INVALID = Pattern.compile("[^a-z0-9]+");
    public static final int MAX_LENGTH = 90;

    public Slug {
        if (value == null || value.isBlank()) {
            throw new DomainException("SLUG_INVALID", "El slug no puede estar vacío");
        }
        if (!value.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new DomainException("SLUG_INVALID", "Slug con formato inválido: " + value);
        }
    }

    public static Slug fromTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new DomainException("SLUG_INVALID", "No se puede generar un slug sin título");
        }
        String normalized = Normalizer.normalize(title, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase(Locale.ROOT)
                .trim();
        String slug = INVALID.matcher(normalized).replaceAll("-").replaceAll("^-+|-+$", "");
        if (slug.length() > MAX_LENGTH) {
            slug = slug.substring(0, MAX_LENGTH).replaceAll("-+$", "");
        }
        return new Slug(slug);
    }

    /** Sufijo determinista para resolver colisiones: titular, titular-2, titular-3... */
    public Slug withSuffix(int attempt) {
        String suffix = "-" + attempt;
        String base = value.length() + suffix.length() > MAX_LENGTH
                ? value.substring(0, MAX_LENGTH - suffix.length())
                : value;
        return new Slug(base + suffix);
    }

    @Override
    public String toString() {
        return value;
    }
}
