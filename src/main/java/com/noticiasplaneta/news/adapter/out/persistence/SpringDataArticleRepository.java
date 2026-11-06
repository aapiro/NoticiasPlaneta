package com.noticiasplaneta.news.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface SpringDataArticleRepository extends JpaRepository<ArticleJpaEntity, UUID> {

    boolean existsByLanguageAndSlug(String language, String slug);

    Optional<ArticleJpaEntity> findByIngestItemId(String ingestItemId);

    boolean existsByIngestItemId(String ingestItemId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update article set embedding = cast(:embedding as vector) where id = :id",
            nativeQuery = true)
    void updateEmbedding(@org.springframework.data.repository.query.Param("id") java.util.UUID id,
                         @org.springframework.data.repository.query.Param("embedding") String embedding);
}
