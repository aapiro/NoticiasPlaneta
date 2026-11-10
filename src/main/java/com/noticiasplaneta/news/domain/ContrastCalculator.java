package com.noticiasplaneta.news.domain;

import java.util.List;

/** Regla de independencia: dominios raíz distintos, sin caídas ni peso 0. */
public final class ContrastCalculator {

    private ContrastCalculator() {
    }

    public static int independentCount(List<ArticleSource> sources) {
        return (int) sources.stream()
                .filter(source -> !source.unreachable())
                .filter(source -> source.weight() > 0)
                .map(ArticleSource::rootDomain)
                .distinct()
                .count();
    }
}
