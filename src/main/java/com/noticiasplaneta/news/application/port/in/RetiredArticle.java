package com.noticiasplaneta.news.application.port.in;

/** Artículo retirado: sigue existiendo, pero ya no se publica (F07, RN1). */
public record RetiredArticle(String id, String slug, String sectionId, String title,
                             String reason, String unpublishedLabel) {
}
