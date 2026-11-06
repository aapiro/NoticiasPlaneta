package com.noticiasplaneta.taxonomy.domain;

import com.noticiasplaneta.shared.domain.DomainException;

import java.util.UUID;

/** Un tema es un hilo editorial con línea temporal propia (F11). */
public record Topic(UUID id, String slug, String title, String summary) {

    public Topic {
        if (slug == null || slug.isBlank()) {
            throw new DomainException("TOPIC_SLUG_REQUIRED", "El tema necesita slug");
        }
        if (title == null || title.isBlank()) {
            throw new DomainException("TOPIC_TITLE_REQUIRED", "El tema necesita título");
        }
    }
}
