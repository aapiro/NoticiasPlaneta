package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleSourceView;
import com.noticiasplaneta.news.application.port.in.ArticleSourcesView;
import com.noticiasplaneta.news.application.port.in.GetArticleSourcesUseCase;
import com.noticiasplaneta.news.application.port.in.LinkSourceCommand;
import com.noticiasplaneta.news.application.port.in.LinkSourceUseCase;
import com.noticiasplaneta.news.application.port.in.RecalculateContrastUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.application.port.out.ArticleSourceRepository;
import com.noticiasplaneta.news.application.port.out.ArticleSourceRepository.ContrastSnapshot;
import com.noticiasplaneta.news.application.port.out.UrlChecker;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleSource;
import com.noticiasplaneta.news.domain.ContrastCalculator;
import com.noticiasplaneta.news.domain.ContrastLevel;
import com.noticiasplaneta.news.domain.RootDomain;
import com.noticiasplaneta.news.domain.SourceKind;
import com.noticiasplaneta.shared.domain.DomainException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** F04 — vincula fuentes, comprueba URLs y persiste el contraste. */
public class ArticleSourceService
        implements LinkSourceUseCase, RecalculateContrastUseCase, GetArticleSourcesUseCase {

    private final ArticleRepository articles;
    private final ArticleSourceRepository sources;
    private final UrlChecker checker;
    private final Clock clock;

    public ArticleSourceService(ArticleRepository articles, ArticleSourceRepository sources,
                                UrlChecker checker, Clock clock) {
        this.articles = articles;
        this.sources = sources;
        this.checker = checker;
        this.clock = clock;
    }

    @Override
    public ArticleSourcesView link(LinkSourceCommand command) {
        ArticleId articleId = parseArticleId(command.articleId());
        articles.findById(articleId).orElseThrow(() -> new DomainException(
                "ARTICLE_NOT_FOUND", "No existe el artículo " + command.articleId()));

        String url = command.url() == null ? "" : command.url().trim();
        String rootDomain = RootDomain.of(url);
        UrlChecker.Check check = safeCheck(url);
        SourceKind kind = command.kind() == null ? SourceKind.PRENSA : command.kind();
        int weight = command.weight() == null ? 1 : Math.clamp(command.weight(), 0, 10);

        sources.save(new ArticleSource(
                UUID.randomUUID(),
                articleId.value(),
                url,
                rootDomain,
                kind,
                check.status(),
                check.checkedAt(),
                weight,
                !check.reachable()));

        return recalculate(articleId.value());
    }

    @Override
    public ArticleSourcesView recalculate(String articleId) {
        return recalculate(parseArticleId(articleId).value());
    }

    @Override
    public ArticleSourcesView get(String articleId) {
        UUID id = parseArticleId(articleId).value();
        ContrastSnapshot snapshot = sources.contrast(id)
                .orElse(new ContrastSnapshot(0, ContrastLevel.LOW.name()));
        List<ArticleSource> list = sources.findByArticleId(id);
        return view(id, snapshot.independentSourceCount(),
                ContrastLevel.valueOf(snapshot.contrastLevel()), list);
    }

    private ArticleSourcesView recalculate(UUID articleId) {
        List<ArticleSource> list = sources.findByArticleId(articleId);
        int count = ContrastCalculator.independentCount(list);
        ContrastLevel level = ContrastLevel.from(count);
        sources.updateContrast(articleId, count, level);
        return view(articleId, count, level, list);
    }

    private UrlChecker.Check safeCheck(String url) {
        try {
            return checker.check(url);
        } catch (RuntimeException e) {
            return new UrlChecker.Check(0, false, clock.instant());
        }
    }

    private static ArticleId parseArticleId(String raw) {
        try {
            return ArticleId.of(raw);
        } catch (IllegalArgumentException e) {
            throw new DomainException("ARTICLE_NOT_FOUND", "Artículo inválido: " + raw);
        }
    }

    private static ArticleSourcesView view(UUID articleId, int count, ContrastLevel level,
                                           List<ArticleSource> list) {
        List<ArticleSourceView> views = list.stream()
                .map(source -> new ArticleSourceView(
                        source.id().toString(),
                        source.url(),
                        source.rootDomain(),
                        source.kind().name(),
                        source.httpStatus(),
                        source.unreachable(),
                        source.weight(),
                        source.checkedAt() == null ? null : source.checkedAt().toString()))
                .toList();
        return new ArticleSourcesView(articleId.toString(), count, level.name(), views);
    }
}
