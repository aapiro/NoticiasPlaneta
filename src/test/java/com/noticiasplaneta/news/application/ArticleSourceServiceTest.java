package com.noticiasplaneta.news.application;

import com.noticiasplaneta.news.application.port.in.ArticleSourcesView;
import com.noticiasplaneta.news.application.port.in.LinkSourceCommand;
import com.noticiasplaneta.news.application.service.ArticleSourceService;
import com.noticiasplaneta.news.application.port.out.UrlChecker;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleOrigin;
import com.noticiasplaneta.news.domain.ContrastLevel;
import com.noticiasplaneta.news.domain.Slug;
import com.noticiasplaneta.news.domain.SourceKind;
import com.noticiasplaneta.news.support.InMemoryArticleSources;
import com.noticiasplaneta.news.support.InMemoryArticles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("F04 · Caso de uso: fuentes y contraste")
class ArticleSourceServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-10T08:00:00Z");
    private static final String ECONOMIA = "economia";

    private InMemoryArticles articles;
    private InMemoryArticleSources sources;
    private ArticleSourceService service;
    private Article article;

    @BeforeEach
    void setUp() {
        articles = new InMemoryArticles();
        sources = new InMemoryArticleSources();
        UrlChecker checker = url -> new UrlChecker.Check(200, true, NOW);
        service = new ArticleSourceService(articles, sources, checker,
                Clock.fixed(NOW, ZoneOffset.UTC));

        article = articles.save(Article.draft("es", ECONOMIA, ArticleOrigin.MANUAL, null,
                Slug.fromTitle("Reforma fiscal"), "Reforma fiscal", "Cuerpo", NOW));
    }

    @Test
    @DisplayName("Dos URLs del mismo medio suman una fuente; otra distinta suma dos")
    void calculaContraste() {
        service.link(new LinkSourceCommand(article.id().toString(),
                "https://elpais.com/a", SourceKind.PRENSA, 1));
        service.link(new LinkSourceCommand(article.id().toString(),
                "https://www.elpais.com/b", SourceKind.PRENSA, 1));

        ArticleSourcesView dos = service.get(article.id().toString());
        assertThat(dos.independentSourceCount()).isEqualTo(1);
        assertThat(dos.contrastLevel()).isEqualTo("LOW");
        assertThat(dos.sources()).hasSize(2);

        ArticleSourcesView tres = service.link(new LinkSourceCommand(article.id().toString(),
                "https://boe.es/x", SourceKind.OFICIAL, 1));
        assertThat(tres.independentSourceCount()).isEqualTo(2);
        assertThat(tres.contrastLevel()).isEqualTo("MEDIUM");
        assertThat(sources.contrast(article.id().value()).orElseThrow().contrastLevel())
                .isEqualTo(ContrastLevel.MEDIUM.name());
    }

    @Test
    @DisplayName("Una fuente caída o con peso 0 no suma")
    void noSumaCaidasNiPesoCero() {
        UrlChecker checker = url -> {
            if (url.contains("caido")) {
                return new UrlChecker.Check(404, false, NOW);
            }
            return new UrlChecker.Check(200, true, NOW);
        };
        ArticleSourceService local = new ArticleSourceService(articles, sources, checker,
                Clock.fixed(NOW, ZoneOffset.UTC));

        local.link(new LinkSourceCommand(article.id().toString(),
                "https://caido.es/x", SourceKind.PRENSA, 1));
        local.link(new LinkSourceCommand(article.id().toString(),
                "https://peso-cero.es/x", SourceKind.PRENSA, 0));

        ArticleSourcesView view = local.get(article.id().toString());
        assertThat(view.independentSourceCount()).isZero();
        assertThat(view.contrastLevel()).isEqualTo("LOW");
        assertThat(view.sources()).hasSize(2);
        assertThat(view.sources().getFirst().unreachable()).isTrue();
    }

    @Test
    @DisplayName("La misma URL no se duplica")
    void noDuplicaUrl() {
        service.link(new LinkSourceCommand(article.id().toString(),
                "https://elpais.com/a", SourceKind.PRENSA, 1));
        service.link(new LinkSourceCommand(article.id().toString(),
                "https://elpais.com/a", SourceKind.OFICIAL, 1));

        assertThat(service.get(article.id().toString()).sources()).hasSize(1);
    }
}
