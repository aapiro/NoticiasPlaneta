package com.noticiasplaneta.autopublish.application.port.in;

import java.time.Instant;
import java.util.List;

/**
 * F6 — pasada de publicación automática y estado para el panel.
 *
 * <p>Una pasada hace dos cosas, en este orden: clasifica los borradores nuevos con el
 * modelo y después publica lo que ya está clasificado y cabe en el cupo del día. Así un
 * fallo del proveedor no impide publicar lo que ya se clasificó, y apagar el interruptor
 * no deja trabajo a medias.
 */
public interface AutoPublishRunUseCase {

    /** Ejecuta una pasada completa. */
    Report runNow();

    Report lastRun();

    /** Estado por sección: interruptor, cupo del día y piezas publicadas hoy. */
    List<SectionStatus> sections();

    record Report(Instant at, String outcome, int classified, int published, int skipped,
                  List<String> notes) {
        public static Report off(Instant at) {
            return new Report(at, "apagado", 0, 0, 0,
                    List.of("El interruptor global (PUBLISH_AUTO) esta apagado"));
        }
    }

    record SectionStatus(String slug, String nombre, boolean activa, boolean autoPublica,
                         String enfoque, int limiteDiario, long publicadasHoy) {
    }
}
