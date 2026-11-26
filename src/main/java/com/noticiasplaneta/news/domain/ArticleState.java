package com.noticiasplaneta.news.domain;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    /**
     * Camino más corto hasta {@code target} siguiendo las aristas permitidas, sin incluir
     * el estado de partida. Lista vacía = inalcanzable (o ya se está en el destino).
     *
     * <p>Es la pieza que permite publicar un borrador de una acción sin inventar una arista
     * DRAFT → PUBLISHED: el agregado recorre los estados que la máquina sí admite.
     */
    public List<ArticleState> pathTo(ArticleState target) {
        if (target == null || target == this) {
            return List.of();
        }
        Map<ArticleState, ArticleState> anterior = new EnumMap<>(ArticleState.class);
        Deque<ArticleState> pendientes = new ArrayDeque<>();
        anterior.put(this, this);
        pendientes.add(this);
        while (!pendientes.isEmpty()) {
            ArticleState actual = pendientes.poll();
            for (ArticleState siguiente : values()) {
                if (!actual.canTransitionTo(siguiente) || anterior.containsKey(siguiente)) {
                    continue;
                }
                anterior.put(siguiente, actual);
                if (siguiente == target) {
                    return reconstruir(anterior, target);
                }
                pendientes.add(siguiente);
            }
        }
        return List.of();
    }

    private List<ArticleState> reconstruir(Map<ArticleState, ArticleState> anterior, ArticleState target) {
        LinkedList<ArticleState> camino = new LinkedList<>();
        for (ArticleState paso = target; paso != this; paso = anterior.get(paso)) {
            camino.addFirst(paso);
        }
        return List.copyOf(camino);
    }
}
