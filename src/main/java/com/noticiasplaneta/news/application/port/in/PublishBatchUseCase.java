package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.shared.domain.Actor;

import java.util.List;

/**
 * F5 — publicación por lotes desde la bandeja.
 *
 * <p>Publica lo seleccionado en una sola acción editorial. Recorre la máquina de estados
 * existente artículo a artículo, así que lo que no se puede publicar se informa y no se
 * fuerza: el lote no es una transacción todo-o-nada.
 */
public interface PublishBatchUseCase {

    /** Tope por petición: la bandeja pinta 40 filas; 100 deja aire sin abrir un POST infinito. */
    int MAX_IDS = 100;

    BatchPublishResult publish(BatchPublishCommand command);

    /** @param articleIds ids tal y como llegan del formulario; se deduplican */
    record BatchPublishCommand(List<String> articleIds, Actor actor) {
    }

    /**
     * @param requested cuántos ids distintos se pidieron
     * @param published publicados en esta llamada, con su titular para el resumen
     * @param skipped   los que no se publicaron y por qué, en castellano
     */
    record BatchPublishResult(int requested, List<Published> published, List<Skipped> skipped) {

        public record Published(String id, String title) {
        }

        public record Skipped(String id, String title, String reason) {
        }

        public boolean isEmpty() {
            return requested == 0;
        }
    }
}
