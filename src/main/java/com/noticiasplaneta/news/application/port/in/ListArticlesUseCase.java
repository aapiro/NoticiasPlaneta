package com.noticiasplaneta.news.application.port.in;

import java.util.List;
import java.util.Map;

public interface ListArticlesUseCase {

    List<ArticleRow> list(String state, String sectionId, String text, int limit);

    /** Contadores por estado, para la cabecera de la bandeja. */
    Map<String, Long> countsByState();

    /** Traza de auditoría de un artículo, para el editor. */
    List<AuditEntry> auditOf(String articleId);

    record AuditEntry(String actor, String action, String before, String after, String at) {
    }

    /** Transiciones posibles desde un estado, según el dominio. Devuelve nombres, no enums. */
    List<String> transitionsFrom(String state);

    /** Tipos de retirada admitidos (RETIRADA, CORRECCION, RECTIFICACION). */
    List<String> retirementKinds();
}
