package com.noticiasplaneta.ai.adapter.out.persistence;

import com.noticiasplaneta.ai.application.port.out.AiJobRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Component
class AiJobRepositoryAdapter implements AiJobRepository {

    private final SpringDataAiJobRepository jpa;
    private final Clock clock;

    AiJobRepositoryAdapter(SpringDataAiJobRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void enqueueIfAbsent(UUID articleId, String type) {
        if (jpa.findByArticleIdAndType(articleId, type).isPresent()) {
            return;
        }
        AiJobJpaEntity e = new AiJobJpaEntity();
        e.id = UUID.randomUUID();
        e.type = type;
        e.articleId = articleId;
        e.state = "PENDING";
        e.attempts = 0;
        e.costMicros = 0;
        e.createdAt = clock.instant();
        e.updatedAt = e.createdAt;
        jpa.save(e);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Job> findPending(int max) {
        return jpa.findByStateOrderByCreatedAtAsc("PENDING", PageRequest.of(0, max)).stream()
                .map(e -> new Job(e.id, e.articleId, e.type, e.state, e.attempts))
                .toList();
    }

    @Override
    @Transactional
    public void markDone(UUID id, long costMicros) {
        jpa.findById(id).ifPresent(e -> {
            e.state = "DONE";
            e.costMicros = costMicros;
            e.updatedAt = clock.instant();
            jpa.save(e);
        });
    }

    @Override
    @Transactional
    public void markFailed(UUID id, String error) {
        jpa.findById(id).ifPresent(e -> {
            e.attempts++;
            e.lastError = error;
            e.state = e.attempts >= 3 ? "FAILED" : "PENDING";
            e.updatedAt = clock.instant();
            jpa.save(e);
        });
    }
}
