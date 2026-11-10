package com.noticiasplaneta.news.adapter.out.persistence;

import com.noticiasplaneta.news.domain.ArticleState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataArticleRepository extends JpaRepository<ArticleJpaEntity, UUID> {

    boolean existsByLanguageAndSlug(String language, String slug);

    Optional<ArticleJpaEntity> findByIngestItemId(String ingestItemId);

    boolean existsByIngestItemId(String ingestItemId);

    List<ArticleJpaEntity> findByStateAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            ArticleState state, Instant now, Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update article set embedding = cast(:embedding as vector) where id = :id",
            nativeQuery = true)
    void updateEmbedding(@org.springframework.data.repository.query.Param("id") java.util.UUID id,
                         @org.springframework.data.repository.query.Param("embedding") String embedding);
}
