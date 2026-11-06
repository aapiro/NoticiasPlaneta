package com.noticiasplaneta.ai.adapter.in.scheduler;

import com.noticiasplaneta.ai.application.port.in.EnrichArticleUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class EnrichmentScheduler {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentScheduler.class);

    private final EnrichArticleUseCase enrich;

    EnrichmentScheduler(EnrichArticleUseCase enrich) {
        this.enrich = enrich;
    }

    @Scheduled(fixedDelayString = "${ai.worker.delay-ms:30000}", initialDelay = 20000)
    void tick() {
        int done = enrich.processPending(10);
        if (done > 0) {
            log.info("Enriquecidos completados: {}", done);
        }
    }
}
