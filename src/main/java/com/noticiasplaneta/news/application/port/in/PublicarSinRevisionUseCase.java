package com.noticiasplaneta.news.application.port.in;

/**
 * Publicación automática de un borrador, sin revisión humana (F8 + PUBLISH_AUTO).
 *
 * <p>Vive en el módulo de noticias porque es una decisión sobre la máquina de estados,
 * no sobre quién redactó. Quien llama solo puede invocarla cuando la sección tiene
 * {@code auto_publica} y la capacidad {@code PUBLISH_AUTO} está encendida; este servicio
 * no lo comprueba (no conoce secciones) y lo deja escrito en la auditoría.
 */
public interface PublicarSinRevisionUseCase {

    /**
     * Lleva el artículo de {@code DRAFT} a {@code PUBLISHED} por la cadena legal de estados.
     *
     * @return {@code true} si ha quedado publicado; {@code false} si no estaba en borrador
     */
    boolean publicar(String articleId, String motivo);
}
