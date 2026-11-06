package com.noticiasplaneta.news.adapter.out.search;

import com.noticiasplaneta.news.application.port.out.SearchIndexPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fase 1: la búsqueda aún no está implementada (F10). El adaptador existe para que la
 * retirada sea correcta desde el primer día: cuando llegue el índice, esto ya está cableado.
 */
@Component
class NoOpSearchIndexAdapter implements SearchIndexPort {

    private static final Logger log = LoggerFactory.getLogger(NoOpSearchIndexAdapter.class);

    @Override
    public void index(String articleId) {
        log.debug("index article={}", articleId);
    }

    @Override
    public void remove(String articleId) {
        log.info("deindex article={}", articleId);
    }
}
