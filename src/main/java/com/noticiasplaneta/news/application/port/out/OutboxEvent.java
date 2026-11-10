package com.noticiasplaneta.news.application.port.out;

import java.time.Instant;
import java.util.UUID;

/** Evento pendiente de propagar a caché, índice, sitemap o avisos. */
public record OutboxEvent(UUID id, UUID articleId, String eventType, String url, Instant createdAt) {
}
