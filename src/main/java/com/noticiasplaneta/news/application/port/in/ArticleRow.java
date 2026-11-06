package com.noticiasplaneta.news.application.port.in;

/** Fila de la bandeja del panel. */
public record ArticleRow(String id, String slug, String sectionId, String title, String state,
                         String origin, String createdLabel, int publicRevisionCount) {
}
