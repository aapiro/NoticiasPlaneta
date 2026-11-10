package com.noticiasplaneta.shared.domain;

import java.util.Objects;
import java.util.Set;

/**
 * Quién ejecuta una acción. Un agente es un actor más, pero con un techo distinto:
 * nunca puede publicar (RN5 de F05).
 */
public record Actor(String id, ActorType type, Set<String> roles, Set<String> sections) {

    public Actor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        sections = sections == null ? Set.of() : Set.copyOf(sections);
    }

    public static Actor human(String id, Set<String> roles, Set<String> sections) {
        return new Actor(id, ActorType.HUMAN, roles, sections);
    }

    public static Actor agent(String id, Set<String> roles) {
        return new Actor(id, ActorType.AGENT, roles, Set.of());
    }

    /** Actor de sistema para tareas programadas (publicación diferida, outbox). */
    public static Actor system() {
        return new Actor("system", ActorType.SYSTEM, Set.of("ADMIN"), Set.of());
    }

    public boolean hasAnyRole(String... candidates) {
        for (String candidate : candidates) {
            if (roles.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    public boolean coversSection(String sectionId) {
        return sections.contains(sectionId);
    }

    public boolean isAgent() {
        return type == ActorType.AGENT;
    }
}
