package com.noticiasplaneta.news.adapter.in.scheduler;

import com.noticiasplaneta.news.application.port.in.PublishScheduledUseCase;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.ArticleId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

/**
 * F06 — publica los artículos programados cuya hora ya venció.
 *
 * <p>La transacción la abre este adaptador: estado y outbox se guardan juntos.
 */
@Component
@ConditionalOnProperty(name = "publish.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class PublishScheduledJob {

    private static final Logger log = LoggerFactory.getLogger(PublishScheduledJob.class);

    private final ArticleRepository articles;
    private final PublishScheduledUseCase publish;
    private final TransactionTemplate transaction;
    private final Clock clock;

    PublishScheduledJob(ArticleRepository articles, PublishScheduledUseCase publish,
                        PlatformTransactionManager transactionManager, Clock clock) {
        this.articles = articles;
        this.publish = publish;
        this.transaction = new TransactionTemplate(transactionManager);
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${publish.scheduler.delay-ms:30000}", initialDelay = 15000)
    void tick() {
        Instant now = clock.instant();
        for (ArticleId id : articles.findScheduledDue(now, 50)) {
            try {
                transaction.executeWithoutResult(status -> publish.publishOne(id, now));
            } catch (RuntimeException e) {
                log.warn("No se pudo publicar el artículo programado {}: {}", id, e.getMessage());
            }
        }
    }
}
