package com.noticiasplaneta.news.domain;

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
}
