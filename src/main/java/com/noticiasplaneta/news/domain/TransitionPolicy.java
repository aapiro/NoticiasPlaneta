package com.noticiasplaneta.news.domain;

import com.noticiasplaneta.shared.domain.Actor;

import com.noticiasplaneta.shared.domain.DomainException;

/**
 * F05 — Autorización de transiciones.
 *
 * <p>Estas reglas son de negocio, no de infraestructura: por eso viven aquí y se prueban
 * sin Spring. RN5: un agente nunca publica, aunque tenga todos los permisos del mundo.
 */
public final class TransitionPolicy {

    private TransitionPolicy() {
    }

    public static void assertCanPerform(Actor actor, ArticleState from, ArticleState to, String sectionId) {
        if (!from.canTransitionTo(to)) {
            throw new DomainException("ILLEGAL_TRANSITION",
                    "Transición no permitida: %s → %s".formatted(from, to));
        }

        if (to == ArticleState.PUBLISHED) {
            if (actor.isAgent()) {
                throw new DomainException("AGENT_CANNOT_PUBLISH", "Un agente no puede publicar");
            }
            if (!actor.hasAnyRole("CHIEF", "ADMIN")) {
                throw new DomainException("FORBIDDEN", "Solo CHIEF o ADMIN pueden publicar");
            }
        }

        if (to == ArticleState.APPROVED) {
            if (!actor.hasAnyRole("EDITOR", "CHIEF", "ADMIN")) {
                throw new DomainException("FORBIDDEN", "Solo un editor puede aprobar");
            }
            if (actor.hasAnyRole("EDITOR") && !actor.hasAnyRole("CHIEF", "ADMIN")
                    && !actor.coversSection(sectionId)) {
                throw new DomainException("OUT_OF_SCOPE", "El editor no cubre la sección " + sectionId);
            }
        }

        if (to == ArticleState.IN_REVIEW && !actor.isAgent()
                && !actor.hasAnyRole("AUTHOR", "EDITOR", "CHIEF", "ADMIN")) {
            throw new DomainException("FORBIDDEN", "Sin permiso para enviar a revisión");
        }

        if (to == ArticleState.ARCHIVED && !actor.hasAnyRole("CHIEF", "ADMIN")) {
            throw new DomainException("FORBIDDEN", "Solo CHIEF o ADMIN pueden archivar");
        }
    }

    /** Quién puede publicar, sin mirar el estado: lo usa el lote antes de empezar. */
    public static void assertCanPublish(Actor actor) {
        if (actor.isAgent()) {
            throw new DomainException("AGENT_CANNOT_PUBLISH", "Un agente no puede publicar");
        }
        if (!actor.hasAnyRole("CHIEF", "ADMIN")) {
            throw new DomainException("FORBIDDEN", "Solo CHIEF o ADMIN pueden publicar");
        }
    }

    /**
     * Publicación inmediata (F5, por lotes) desde cualquier estado del que la máquina
     * llegue a PUBLISHED. No se salta el dominio: el camino se calcula con
     * {@link ArticleState#pathTo} y cada arista se vuelve a autorizar en el agregado.
     */
    public static void assertCanPublishNow(Actor actor, ArticleState from, String sectionId) {
        assertCanPublish(actor);
        if (from == ArticleState.PUBLISHED) {
            throw new DomainException("ALREADY_PUBLISHED", "El artículo ya está publicado");
        }
        if (from.isTerminal()) {
            throw new DomainException("ARTICLE_ARCHIVED", "Un artículo archivado no se publica");
        }
        if (from.pathTo(ArticleState.PUBLISHED).isEmpty()) {
            throw new DomainException("NOT_PUBLISHABLE",
                    "Desde %s no se llega a PUBLISHED por la máquina de estados".formatted(from));
        }
    }
}
