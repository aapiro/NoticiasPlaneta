package com.noticiasplaneta.ingest.application.service;

import com.noticiasplaneta.ingest.application.port.in.BuscarMaterialUseCase;
import com.noticiasplaneta.ingest.application.port.out.MaterialQueryRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Búsqueda del material que puede sostener una pieza (F8).
 *
 * <p>Ventana de 30 días: una noticia se escribe con lo publicado, no con el archivo.
 * Si el índice de texto completo no devuelve nada, se reintenta por palabras sueltas
 * (AND de {@code like}) para no perder temas que el stemmer español no casa.
 */
public class BuscarMaterialService implements BuscarMaterialUseCase {

    public static final int VENTANA_DIAS = 30;
    public static final int MAX_LIMITE = 50;
    private static final int MIN_CONSULTA = 3;
    private static final int MAX_TERMINOS = 6;
    private static final Set<String> SIN_APORTE = Set.of(
            "de", "la", "el", "los", "las", "un", "una", "unos", "unas", "y", "o", "u",
            "que", "en", "con", "por", "para", "del", "al", "a", "se", "su", "sus",
            "sobre", "tras", "como", "mas", "pero", "sin", "es", "son", "han", "ha");

    private final MaterialQueryRepository repositorio;

    public BuscarMaterialService(MaterialQueryRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public List<Material> buscar(String consulta, int limite) {
        String limpia = consulta == null ? "" : consulta.replaceAll("\\s+", " ").trim();
        if (limpia.length() < MIN_CONSULTA) {
            return List.of();
        }
        int max = Math.clamp(limite, 1, MAX_LIMITE);
        return repositorio.buscar(limpia, terminos(limpia), max, VENTANA_DIAS).stream()
                .map(f -> new Material(f.itemId(), f.medio(), f.url(), f.fecha(), f.titular(), f.texto()))
                .toList();
    }

    /** Palabras con carga semántica para el respaldo de búsqueda: sin acentos, sin stopwords. */
    static List<String> terminos(String consulta) {
        return java.util.Arrays.stream(Normalizer.normalize(consulta, Normalizer.Form.NFD)
                        .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                        .toLowerCase(Locale.ROOT)
                        .split("[^a-z0-9]+"))
                .filter(t -> t.length() >= 3)
                .filter(t -> !SIN_APORTE.contains(t))
                .distinct()
                .limit(MAX_TERMINOS)
                .toList();
    }
}
