package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.ArticleRow;
import com.noticiasplaneta.news.application.port.in.ListArticlesUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleAdminQueryRepository;

import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.news.domain.UnpublishKind;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ArticleAdminQueryService implements ListArticlesUseCase {

    private final ArticleAdminQueryRepository repository;

    public ArticleAdminQueryService(ArticleAdminQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<ArticleRow> list(String state, String sectionId, String text, int limit) {
        return repository.list(
                state == null || state.isBlank() ? null : state.toUpperCase(),
                sectionId == null || sectionId.isBlank() ? null : sectionId,
                text == null || text.isBlank() ? null : text.trim(),
                Math.clamp(limit, 1, 100));
    }

    @Override
    public Map<String, Long> countsByState() {
        return repository.countsByState();
    }

    @Override
    public List<String> transitionsFrom(String state) {
        ArticleState from = ArticleState.valueOf(state);
        return Arrays.stream(ArticleState.values())
                .filter(from::canTransitionTo)
                .map(Enum::name)
                .toList();
    }

    @Override
    public List<String> retirementKinds() {
        return Arrays.stream(UnpublishKind.values()).map(Enum::name).toList();
    }

    @Override
    public List<AuditEntry> auditOf(String articleId) {
        return repository.auditOf(articleId).stream()
                .map(r -> new AuditEntry(r.actor(), r.action(), r.before(), r.after(), r.at()))
                .toList();
    }
}
