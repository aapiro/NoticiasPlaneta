package com.noticiasplaneta.news.domain;

import java.util.Set;

/** Máquina de estados del artículo. Vive en el dominio, no en el controlador. */
public enum ArticleState {

    DRAFT,
    IN_REVIEW,
    APPROVED,
    SCHEDULED,
    PUBLISHED,
    ARCHIVED;

    private static final Set<String> TRANSITIONS = Set.of(
            "DRAFT>IN_REVIEW",
            "IN_REVIEW>APPROVED",
            "IN_REVIEW>DRAFT",
            "APPROVED>SCHEDULED",
            "APPROVED>DRAFT",
            "SCHEDULED>PUBLISHED",
            "SCHEDULED>APPROVED",
            "PUBLISHED>ARCHIVED");

    public boolean canTransitionTo(ArticleState target) {
        return TRANSITIONS.contains(name() + ">" + target.name());
    }

    public boolean isTerminal() {
        return this == ARCHIVED;
    }
}
