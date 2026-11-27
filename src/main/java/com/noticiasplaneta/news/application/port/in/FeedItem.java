package com.noticiasplaneta.news.application.port.in;

import java.time.Instant;

/**
 * Pieza tal como sale en el RSS (backlog #5).
 *
 * <p>Lleva el cuerpo porque el feed necesita un resumen propio; {@code PublishedArticle}
 * solo trae la nota de corrección.
 */
public record FeedItem(String id, String sectionId, String slug, String title,
                       String body, Instant publishedAt, String imageUrl) {
}
