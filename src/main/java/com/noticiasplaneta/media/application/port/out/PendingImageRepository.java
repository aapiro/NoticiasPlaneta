package com.noticiasplaneta.media.application.port.out;

import java.util.List;

/** Artículos que tienen foto en el feed pero no en casa (backlog #2). */
public interface PendingImageRepository {

    /**
     * Publicados con {@code image_url} en su item de ingesta y sin media adjunta.
     *
     * @param limit tope de la tanda, para no bloquear el hilo del panel
     */
    List<PendingImage> findPending(int limit);

    /** Cuántos quedan pendientes, sin traerlos. */
    long countPending();

    record PendingImage(String articleId, String imageUrl, String title) {
    }
}
