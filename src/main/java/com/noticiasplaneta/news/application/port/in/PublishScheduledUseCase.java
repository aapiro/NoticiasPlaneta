package com.noticiasplaneta.news.application.port.in;

import com.noticiasplaneta.news.domain.ArticleId;

import java.time.Instant;

/** F06: publica un artículo programado cuando vence su hora. */
public interface PublishScheduledUseCase {

    /**
     * Publica un artículo si sigue en SCHEDULED y su hora ya venció.
     *
     * @return true si lo publicó; false si ya no procedía
     */
    boolean publishOne(ArticleId articleId, Instant now);
}
