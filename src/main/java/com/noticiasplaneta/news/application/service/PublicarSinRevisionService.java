package com.noticiasplaneta.news.application.service;

import com.noticiasplaneta.news.application.port.in.PublicarSinRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.application.port.out.ArticleRepository;
import com.noticiasplaneta.news.domain.Article;
import com.noticiasplaneta.news.domain.ArticleId;
import com.noticiasplaneta.news.domain.ArticleState;
import com.noticiasplaneta.shared.domain.Actor;
import com.noticiasplaneta.shared.domain.AuditEntry;
import com.noticiasplaneta.shared.domain.AuditPort;

import java.time.Clock;
import java.util.Optional;

/**
 * F8 — publicación de un borrador generado sin revisión humana (solo PUBLISH_AUTO).
 *
 * <p>Recorre la cadena legal de estados con el actor de sistema: no se salta la máquina
 * de estados ni se publica un DRAFT de un salto, porque eso dejaría un agujero que
 * cualquier otro flujo podría reutilizar. Cada transición queda en {@code audit_log}
 * por {@link TransitionArticleUseCase}, y aquí se añade una entrada explícita que dice
 * que no hubo revisión humana.
 */
public class PublicarSinRevisionService implements PublicarSinRevisionUseCase {

    static final String MOTIVO_POR_DEFECTO =
            "Publicación automática de un borrador generado con IA (PUBLISH_AUTO, sin revisión humana)";

    private final ArticleRepository articulos;
    private final TransitionArticleUseCase transiciones;
    private final AuditPort auditoria;
    private final Clock reloj;

    public PublicarSinRevisionService(ArticleRepository articulos,
                                      TransitionArticleUseCase transiciones,
                                      AuditPort auditoria, Clock reloj) {
        this.articulos = articulos;
        this.transiciones = transiciones;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Override
    public boolean publicar(String articleId, String motivo) {
        ArticleId id = ArticleId.of(articleId);
        Optional<Article> quizas = articulos.findById(id);
        if (quizas.isEmpty() || quizas.get().state() != ArticleState.DRAFT) {
            return false;
        }
        String razon = motivo == null || motivo.isBlank() ? MOTIVO_POR_DEFECTO : motivo;
        Actor sistema = Actor.system();

        for (ArticleState paso : new ArticleState[]{
                ArticleState.IN_REVIEW, ArticleState.APPROVED,
                ArticleState.SCHEDULED, ArticleState.PUBLISHED}) {
            transiciones.transition(new TransitionCommand(id, paso, sistema, razon));
        }

        auditoria.record(new AuditEntry(sistema.id(), sistema.type().name(),
                "ARTICLE_PUBLISHED_WITHOUT_REVIEW", "Article", articleId,
                ArticleState.DRAFT.name(), ArticleState.PUBLISHED.name(), reloj.instant()));

        return articulos.findById(id)
                .map(a -> a.state() == ArticleState.PUBLISHED)
                .orElse(false);
    }
}
