package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.Slug;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class ArticleRepositoryAdapter implements ArticleRepository {

    private final SpringDataArticleRepository jpa;

    ArticleRepositoryAdapter(SpringDataArticleRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional
    public Article save(Article article) {
        return toDomain(jpa.save(toEntity(article)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Article> findById(ArticleId id) {
        return jpa.findById(id.value()).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySlug(String language, Slug slug) {
        return jpa.existsByLanguageAndSlug(language, slug.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByIngestItemId(String ingestItemId) {
        return jpa.existsByIngestItemId(ingestItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Article> findByIngestItemId(String ingestItemId) {
        return jpa.findByIngestItemId(ingestItemId).map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return jpa.count();
    }

    @Override
    @Transactional
    public void saveEmbedding(ArticleId id, float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(embedding[i]);
        }
        jpa.updateEmbedding(id.value(), sb.append(']').toString());
    }

    private ArticleJpaEntity toEntity(Article a) {
        ArticleJpaEntity e = new ArticleJpaEntity();
        e.id = a.id().value();
        e.language = a.language();
        e.sectionId = a.sectionId();
        e.origin = a.origin();
        e.ingestItemId = a.ingestItemId();
        e.slug = a.slug().value();
        e.title = a.title();
        e.body = a.body();
        e.state = a.state();
        e.revisionCount = a.revisionCount();
        e.publishedAt = a.publishedAt();
        e.scheduledAt = a.scheduledAt();
        e.createdAt = a.createdAt();
        e.unpublishedAt = a.unpublishedAt();
        e.unpublishReason = a.unpublishReason();
        e.correctionNote = a.correctionNote();
        return e;
    }

    private Article toDomain(ArticleJpaEntity e) {
        return Article.rehydrate(
                new ArticleId(e.id), e.language, e.sectionId, e.origin, e.ingestItemId,
                new Slug(e.slug), e.title, e.body, e.state, e.revisionCount,
                e.publishedAt, e.scheduledAt, e.createdAt,
                e.unpublishedAt, e.unpublishReason, e.correctionNote);
    }
}
