package com.noticiasplaneta.edicion.application.port.in;

/** Pieza publicada dentro de una ejecución, para enlazar desde el histórico. */
public record PiezaEjecucionView(String articleId, String sectionId, String titulo, String url) {
}
