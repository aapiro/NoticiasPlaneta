package com.noticiasplaneta.ai.adapter.in.event;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import com.noticiasplaneta.ai.application.port.out.AiCapabilities;
import com.noticiasplaneta.ai.domain.AiCapability;
import com.noticiasplaneta.shared.events.ArticleStateChanged;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al publicar, se encola el enriquecido. Fuera de la ruta del request: la publicación
 * no espera al modelo.
 *
 * <p>Con la capacidad apagada no se encola nada: evita llenar la cola de jobs que
 * nacerían condenados a fallar con {@code AI_CAPABILITY_DISABLED}.
 */
@Component
class ArticlePublishedListener {

    private final EnrichArticleUseCase enrich;
    private final AiCapabilities capabilities;

    ArticlePublishedListener(EnrichArticleUseCase enrich, AiCapabilities capabilities) {
        this.enrich = enrich;
        this.capabilities = capabilities;
    }

    @EventListener
    void on(ArticleStateChanged event) {
        if (!"PUBLISHED".equals(event.to())) return;
        if (!capabilities.isOn(AiCapability.ENRICH_TAGS)) return;

        enrich.enqueue(event.articleId());
    }
}
