package com.noticiasplaneta.ai.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataAiJobRepository extends JpaRepository<AiJobJpaEntity, UUID> {

    Optional<AiJobJpaEntity> findByArticleIdAndType(UUID articleId, String type);

    List<AiJobJpaEntity> findByStateOrderByCreatedAtAsc(String state, org.springframework.data.domain.Pageable page);
}
