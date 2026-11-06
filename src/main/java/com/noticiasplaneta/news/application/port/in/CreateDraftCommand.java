package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleOrigin;

public record CreateDraftCommand(
        String ingestItemId,
        String language,
        String sectionId,
        String title,
        String body,
        String sourceUrl,
        ArticleOrigin origin) {

    /** Borrador creado por un agente: el origen lo fija el módulo de noticias, no el llamante. */
    public static CreateDraftCommand byAgent(String ingestItemId, String sectionId,
                                             String title, String body, String sourceUrl) {
        return new CreateDraftCommand(ingestItemId, "es", sectionId, title, body, sourceUrl,
                ArticleOrigin.AGENT);
    }
}
