package com.noticiasplaneta.generation.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Tema o titular de entrada del generador (F8).
 *
 * <p>Se valida una sola vez, en el borde: ni un tema vacío ni una novela. Los
 * términos que devuelve {@link #terminos()} sirven para el respaldo de búsqueda
 * cuando el índice de texto completo no encuentra nada.
 */
public record Tema(String valor) {

    public static final int MIN_CARACTERES = 4;
    public static final int MAX_CARACTERES = 300;
    public static final int MAX_TERMINOS = 8;

    /** Palabras sin carga semántica: buscarlas solo añade ruido. */
    private static final Set<String> SIN_APORTE = Set.of(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "u",
            "que", "en", "con", "por", "para", "del", "al", "a", "se", "su", "sus",
            "sobre", "tras", "como", "mas", "pero", "sin", "es", "son", "han", "ha",
            "the", "of", "and", "for");

    public Tema {
        String limpio = valor == null ? "" : valor.replaceAll("\\s+", " ").trim();
        if (limpio.length() < MIN_CARACTERES) {
            throw new DomainException("TEMA_INVALIDO",
                    "El tema necesita al menos " + MIN_CARACTERES + " caracteres");
        }
        if (limpio.length() > MAX_CARACTERES) {
            throw new DomainException("TEMA_INVALIDO",
                    "El tema no puede pasar de " + MAX_CARACTERES + " caracteres");
        }
        valor = limpio;
    }

    public static Tema of(String raw) {
        return new Tema(raw);
    }

    /** Palabras de 3 letras o más, sin acentos y sin stopwords, para la búsqueda de respaldo. */
    public List<String> terminos() {
        return Arrays.stream(Normalizer.normalize(valor, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !SIN_APORTE.contains(t))
                .distinct()
                .limit(MAX_TERMINOS)
                .toList();
    }

    @Override
    public String toString() {
        return valor;
    }
}
