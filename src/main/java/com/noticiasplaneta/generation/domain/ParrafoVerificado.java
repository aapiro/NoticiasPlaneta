package com.noticiasplaneta.generation.domain;

import java.util.List;

/** Párrafo que ha pasado la verificación y las fuentes que lo sostienen. */
public record ParrafoVerificado(String texto, List<Integer> citas) {

    public ParrafoVerificado {
        texto = texto == null ? "" : texto;
        citas = citas == null ? List.of() : List.copyOf(citas);
    }
}
