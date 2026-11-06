package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleRevisionView;
import com.noticiasplaneta.news.application.port.in.ListArticleRevisionsUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.ArticleRevisionRepository;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.shared.domain.TextDiff;

import java.util.ArrayList;
import java.util.List;

/** Historial de versiones con el resumen de qué cambió en cada salto. */
public class ListArticleRevisionsService implements ListArticleRevisionsUseCase {

    private final ArticleRevisionRepository revisions;
    private final ArticleRepository articles;

    public ListArticleRevisionsService(ArticleRevisionRepository revisions, ArticleRepository articles) {
        this.revisions = revisions;
        this.articles = articles;
    }

    @Override
    public List<ArticleRevisionView> revisions(String articleId) {
        List<ArticleRevisionRepository.Snapshot> guardadas = revisions.byArticle(articleId);
        if (guardadas.isEmpty()) {
            return List.of();
        }

        var actual = articles.findById(ArticleId.of(articleId));
        String tituloActual = actual.map(a -> a.title()).orElse("");
        String cuerpoActual = actual.map(a -> a.body()).orElse("");

        // las guardadas vienen de la más nueva a la más vieja; el "después" de cada una
        // es la siguiente más nueva, o el texto actual si es la última guardada
        List<ArticleRevisionView> salida = new ArrayList<>();
        for (int i = 0; i < guardadas.size(); i++) {
            var s = guardadas.get(i);
            String tituloDespues = i == 0 ? tituloActual : guardadas.get(i - 1).title();
            String cuerpoDespues = i == 0 ? cuerpoActual : guardadas.get(i - 1).body();

            List<String> cambios = new ArrayList<>();
            if (TextDiff.cambia(s.title(), tituloDespues)) {
                cambios.add("titular modificado");
            }
            var diff = TextDiff.between(s.body(), cuerpoDespues);
            long quitadas = diff.stream().filter(l -> l.kind() == TextDiff.Kind.QUITADA).count();
            long anadidas = diff.stream().filter(l -> l.kind() == TextDiff.Kind.AÑADIDA).count();
            if (quitadas > 0 || anadidas > 0) {
                cambios.add("cuerpo: −%d / +%d líneas".formatted(quitadas, anadidas));
            }
            if (cambios.isEmpty()) {
                cambios.add("sin cambios en el texto");
            }

            salida.add(new ArticleRevisionView(s.revision(), s.title(), s.body(), s.editedBy(),
                    s.editedAt(), cambios));
        }
        return salida;
    }
}
