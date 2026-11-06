package com.noticiasplaneta.news.application.port.out;

/**
 * Purga de caché. En fase 1 es la caché local de Caddy; en fase 2, un CDN.
 * La retirada tiene prioridad sobre cualquier otra purga (RN2 de F07).
 */
public interface CachePurgePort {

    void purgeImmediately(String url);

    void purgeEventually(String url);
}
