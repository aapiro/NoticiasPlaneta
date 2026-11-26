package com.noticiasplaneta.generation.domain;

import java.util.List;

/** Salida cruda del modelo para un párrafo, antes de verificar. */
public record ParrafoSinVerificar(String texto, List<Integer> citas) {

    public ParrafoSinVerificar {
        texto = texto == null ? "" : texto;
        citas = citas == null ? List.of() : List.copyOf(citas);
    }
}
