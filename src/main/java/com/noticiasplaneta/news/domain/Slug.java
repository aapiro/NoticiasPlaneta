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
        if (value.length() > 120) {
            throw new DomainException("SLUG_INVALID", "Slug demasiado largo: " + value.length());
        }
    }

    /**
     * Slug nuevo a partir de una entrada: aquí sí se exige el formato canónico.
     *
     * <p>La validación de formato vive en esta fábrica y no en el constructor porque el
     * constructor también reconstruye valores que ya están en la base, y ahí el formato es
     * historia (ver {@link #persisted(String)}).
     */
    public static Slug of(String value) {
        Slug slug = new Slug(value);
        if (!slug.value.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
            throw new DomainException("SLUG_INVALID", "Slug con formato inválido: " + value);
        }
        return slug;
    }

    /**
     * Slug que ya está en la base de datos.
     *
     * <p>Existe por los valores heredados del script de edición manual, que truncaba el
     * titular y podía dejar el slug acabado en guion. El formato estricto es una regla de
     * <b>creación</b>; al rehidratar, el valor de la base es la URL canónica (ADR-001) y
     * rechazarla solo consigue un 500 en la lectura de esa pieza. La reparación de los
     * borradores vive en la migración V23; lo publicado no se toca.
     */
    public static Slug persisted(String value) {
        return new Slug(value);
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
        return Slug.of(slug);
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
