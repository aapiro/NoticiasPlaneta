package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleSourcesView;
import com.noticiasplaneta.news.application.port.in.LinkSourceCommand;
import com.noticiasplaneta.news.application.port.in.LinkSourceUseCase;
import com.noticiasplaneta.news.application.port.in.RecalculateContrastUseCase;
import com.noticiasplaneta.news.application.port.in.VincularFuentesGeneradasUseCase;
import com.noticiasplaneta.news.domain.SourceKind;

import java.util.List;
import java.util.Objects;

/**
 * F8 — las fuentes de una pieza generada entran por la misma puerta que las de una pieza
 * normal: {@code article_source}, con comprobación de URL y contraste recalculado (F04).
 * El generador no tiene un camino privilegiado para «apuntar fuentes».
 */
public class VincularFuentesGeneradasService implements VincularFuentesGeneradasUseCase {

    private final LinkSourceUseCase enlazar;
    private final RecalculateContrastUseCase contraste;

    public VincularFuentesGeneradasService(LinkSourceUseCase enlazar,
                                          RecalculateContrastUseCase contraste) {
        this.enlazar = enlazar;
        this.contraste = contraste;
    }

    @Override
    public int vincular(String articleId, List<String> urls) {
        Objects.requireNonNull(articleId, "articleId");
        List<String> limpias = urls == null ? List.of() : urls.stream()
                .filter(u -> u != null && !u.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        for (String url : limpias) {
            enlazar.link(new LinkSourceCommand(articleId, url, SourceKind.PRENSA, 1));
        }
        if (limpias.isEmpty()) {
            return 0;
        }
        ArticleSourcesView vista = contraste.recalculate(articleId);
        return vista.independentSourceCount();
    }
}
