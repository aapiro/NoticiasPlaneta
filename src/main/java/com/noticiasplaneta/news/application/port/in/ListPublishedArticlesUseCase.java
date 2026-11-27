package com.noticiasplaneta.news.application.port.in;

import java.util.List;

public interface ListPublishedArticlesUseCase {

    List<PublishedArticle> latest(String sectionId, int limit);

    /**
     * Página de publicadas (backlog #3).
     *
     * @param sectionId sección, o {@code null} para todo el sitio
     * @param pagina    desde 1
     * @param tamano    piezas por página
     */
    Page paginar(String sectionId, int pagina, int tamano);

    /** Últimas publicadas con cuerpo, para el RSS de salida (backlog #5). */
    List<FeedItem> feed(String sectionId, int limite);

    record Page(List<PublishedArticle> articulos, int pagina, int tamano, long total) {

        public boolean hayAnterior() {
            return pagina > 1;
        }

        public boolean haySiguiente() {
            return (long) pagina * tamano < total;
        }

        public int totalPaginas() {
            return (int) Math.max(1, (total + tamano - 1) / tamano);
        }

        public boolean vacia() {
            return articulos == null || articulos.isEmpty();
        }
    }
}
