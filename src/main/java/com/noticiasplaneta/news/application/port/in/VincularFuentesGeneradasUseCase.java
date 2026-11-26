package com.noticiasplaneta.news.application.port.in;

import java.util.List;

/**
 * Vincula a un artículo recién generado las URLs que lo sostienen (F8).
 *
 * <p>Existe para que el módulo de generación no tenga que construir
 * {@code LinkSourceCommand}, que arrastra tipos del dominio de noticias.
 */
public interface VincularFuentesGeneradasUseCase {

    /**
     * @return número de fuentes independientes (dominios raíz distintos) tras recalcular el contraste
     */
    int vincular(String articleId, List<String> urls);
}
