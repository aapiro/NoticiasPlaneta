package com.noticiasplaneta.generation.domain;

import java.util.List;

/** Lo que devuelve el modelo antes de la verificación determinista. */
public record BorradorSinVerificar(String titular, List<ParrafoSinVerificar> parrafos) {

    public BorradorSinVerificar {
        titular = titular == null ? "" : titular;
        parrafos = parrafos == null ? List.of() : List.copyOf(parrafos);
    }

    public static BorradorSinVerificar vacio() {
        return new BorradorSinVerificar("", List.of());
    }
}
