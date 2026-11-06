package com.noticiasplaneta.ingest.adapter.in.scheduler;

import com.noticiasplaneta.ingest.application.port.in.IngestAllSourcesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Ingesta periódica. Se puede desactivar con {@code ingest.scheduler.enabled=false}
 * (útil en tests y en entornos donde la ingesta la dispara un agente por MCP).
 */
@Component
@ConditionalOnProperty(name = "ingest.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class IngestScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestScheduler.class);

    private final IngestAllSourcesUseCase ingestAll;

    IngestScheduler(IngestAllSourcesUseCase ingestAll) {
        this.ingestAll = ingestAll;
    }

    @Scheduled(fixedDelayString = "${ingest.scheduler.delay-ms:900000}", initialDelay = 30000)
    void tick() {
        int created = ingestAll.runAll();
        if (created > 0) {
            log.info("Ingesta periódica: {} elementos nuevos", created);
        }
    }
}
