package com.noticiasplaneta.news.adapter.out.cache;

import com.noticiasplaneta.news.application.port.out.CachePurgePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Fase 1: no hay CDN delante, así que la purga es una marca de invalidación local.
 * En fase 2 este adaptador habla con Cloudflare sin tocar el dominio (ADR-008).
 */
@Component
class LocalCachePurgeAdapter implements CachePurgePort {

    private static final Logger log = LoggerFactory.getLogger(LocalCachePurgeAdapter.class);

    @Override
    public void purgeImmediately(String url) {
        log.warn("purge-now url={} reason=retirada", url);
    }

    @Override
    public void purgeEventually(String url) {
        log.info("purge-soon url={}", url);
    }
}
