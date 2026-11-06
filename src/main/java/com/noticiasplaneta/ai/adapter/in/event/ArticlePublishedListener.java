package com.noticiasplaneta.ai.adapter.in.event;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al publicar, se encola el enriquecido. Fuera de la ruta del request: la publicación
 * no espera al modelo.
 */
@Component
class ArticlePublishedListener {

    private final EnrichArticleUseCase enrich;

    ArticlePublishedListener(EnrichArticleUseCase enrich) {
        this.enrich = enrich;
    }

    @EventListener
    void on(ArticleStateChanged event) {
        if ("PUBLISHED".equals(event.to())) {
            enrich.enqueue(event.articleId());
        }
    }
}
