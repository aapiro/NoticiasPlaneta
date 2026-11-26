package com.noticiasplaneta.generation.application.port.in;

/** F8 — genera un borrador rastreable a partir de un tema y una sección. */
public interface GenerarBorradorUseCase {

    ResultadoGeneracion generar(GenerarBorradorCommand comando);
}
