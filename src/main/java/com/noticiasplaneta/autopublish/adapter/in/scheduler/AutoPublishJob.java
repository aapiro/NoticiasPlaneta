package com.noticiasplaneta.autopublish.adapter.in.scheduler;

import com.noticiasplaneta.autopublish.application.port.in.AutoPublishRunUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * F6 — pasada periódica de la publicación automática.
 *
 * <p>La transacción de cada pieza la abre el caso de uso de noticias; aquí solo se lanza
 * la pasada. Si el interruptor global está apagado la pasada no hace nada: no se
 * clasifica, no se gasta presupuesto y no se toca la base de datos.
 */
@Component
@ConditionalOnProperty(name = "autopublish.scheduler.enabled", havingValue = "true", matchIfMissing = true)
class AutoPublishJob {

    private static final Logger log = LoggerFactory.getLogger(AutoPublishJob.class);

    private final AutoPublishRunUseCase autoPublish;

    AutoPublishJob(AutoPublishRunUseCase autoPublish) {
        this.autoPublish = autoPublish;
    }

    @Scheduled(fixedDelayString = "${autopublish.scheduler.delay-ms:300000}",
            initialDelayString = "${autopublish.scheduler.initial-delay-ms:45000}")
    void tick() {
        try {
            AutoPublishRunUseCase.Report report = autoPublish.runNow();
            if (report.published() > 0) {
                log.info("Autopublicacion: {} piezas publicadas ({} clasificadas)", report.published(), report.classified());
            }
        } catch (RuntimeException e) {
            log.warn("La pasada de autopublicacion fallo: {}", e.getMessage());
        }
    }
}
