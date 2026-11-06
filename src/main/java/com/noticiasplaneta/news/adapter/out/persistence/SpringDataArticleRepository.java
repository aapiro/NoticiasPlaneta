package com.noticiasplaneta.news.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataArticleRepository extends JpaRepository<ArticleJpaEntity, UUID> {

    boolean existsByLanguageAndSlug(String language, String slug);

    Optional<ArticleJpaEntity> findByIngestItemId(String ingestItemId);

    boolean existsByIngestItemId(String ingestItemId);
}
