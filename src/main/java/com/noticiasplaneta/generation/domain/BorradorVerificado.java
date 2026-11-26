package com.noticiasplaneta.generation.domain;

import java.util.List;

/** Resultado de verificar el borrador del modelo contra los fragmentos que se le dieron. */
public record BorradorVerificado(String titular, List<ParrafoVerificado> parrafos, List<Descarte> descartes) {

    public BorradorVerificado {
        titular = titular == null ? "" : titular;
        parrafos = parrafos == null ? List.of() : List.copyOf(parrafos);
        descartes = descartes == null ? List.of() : List.copyOf(descartes);
    }

    /** Un titular no vacío y al menos un párrafo con fuentes: lo mínimo para que haya pieza. */
    public boolean sirve() {
        return !titular.isBlank() && !parrafos.isEmpty();
    }

    public int caracteres() {
        return parrafos.stream().mapToInt(p -> p.texto().length()).sum();
    }

    /** Fuentes citadas por los párrafos que sobrevivieron, sin repetir y en orden. */
    public List<Integer> citas() {
        return parrafos.stream()
                .flatMap(p -> p.citas().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
