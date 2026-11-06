package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.news.application.port.in.EditArticleCommand;
import com.noticiasplaneta.news.application.port.in.EditArticleUseCase;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.news.application.port.in.ListArticleRevisionsUseCase;
import com.noticiasplaneta.news.application.port.in.ListArticlesUseCase;
import com.noticiasplaneta.news.application.port.in.RestoreArticleRevisionUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionArticleUseCase;
import com.noticiasplaneta.news.application.port.in.TransitionCommand;
import com.noticiasplaneta.news.application.port.in.UnpublishArticleUseCase;
import com.noticiasplaneta.news.application.port.in.UnpublishCommand;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.UUID;

/**
 * Editor del panel.
 *
 * <p>RN1: no reimplementa ninguna regla; delega en los mismos casos de uso que la API.
 * Solo depende de {@code news.application.port.in} y de {@code shared} (lo vigila ArchUnit),
 * así que el módulo no se acopla al modelo interno de noticias.
 */
@Controller
class AdminArticleController {

    private final GetArticleTextUseCase articleText;
    private final ListArticlesUseCase bandeja;
    private final TransitionArticleUseCase transition;
    private final UnpublishArticleUseCase unpublish;
    private final EditArticleUseCase editArticle;
    private final ListArticleRevisionsUseCase historial;
    private final RestoreArticleRevisionUseCase restaurar;

    AdminArticleController(GetArticleTextUseCase articleText, ListArticlesUseCase bandeja,
                           TransitionArticleUseCase transition, UnpublishArticleUseCase unpublish,
                           EditArticleUseCase editArticle, ListArticleRevisionsUseCase historial,
                           RestoreArticleRevisionUseCase restaurar) {
        this.articleText = articleText;
        this.bandeja = bandeja;
        this.transition = transition;
        this.unpublish = unpublish;
        this.editArticle = editArticle;
        this.historial = historial;
        this.restaurar = restaurar;
    }

    @GetMapping("/admin/articulos/{id}")
    ModelAndView editor(@PathVariable String id,
                        @RequestParam(required = false) String guardado,
                        @RequestParam(required = false) String restaurado) {
        ModelAndView mav = render(id, null, null);
        mav.addObject("guardado", guardado != null);
        mav.addObject("restaurado", restaurado);
        return mav;
    }

    @PostMapping("/admin/articulos/{id}/editar")
    ModelAndView editar(@PathVariable String id,
                        @RequestParam String title,
                        @RequestParam String body,
                        @RequestParam int revision,
                        Authentication authentication) {
        try {
            editArticle.edit(new EditArticleCommand(id, title, body, revision,
                    AdminActor.from(authentication)));
            return new ModelAndView("redirect:/admin/articulos/" + id + "?guardado=1");
        } catch (DomainException e) {
            return render(id, statusFor(e), e.getMessage());
        }
    }

    @PostMapping("/admin/articulos/{id}/restaurar")
    ModelAndView restaurarVersion(@PathVariable String id,
                                  @RequestParam int revision,
                                  Authentication authentication) {
        try {
            restaurar.restore(id, revision, AdminActor.from(authentication));
            return new ModelAndView("redirect:/admin/articulos/" + id + "?restaurado=" + revision);
        } catch (DomainException e) {
            return render(id, statusFor(e), e.getMessage());
        }
    }

    @PostMapping("/admin/articulos/{id}/transicion")
    ModelAndView transicion(@PathVariable String id,
                            @RequestParam String destino,
                            @RequestParam(required = false) String motivo,
                            Authentication authentication) {
        try {
            transition.transition(TransitionCommand.of(id, destino, AdminActor.from(authentication), motivo));
            return new ModelAndView("redirect:/admin/articulos/" + id);
        } catch (DomainException e) {
            return render(id, statusFor(e), e.getMessage());
        } catch (IllegalArgumentException e) {
            return render(id, HttpStatus.BAD_REQUEST, "Transición desconocida: " + destino);
        }
    }

    @PostMapping("/admin/articulos/{id}/retirar")
    ModelAndView retirar(@PathVariable String id,
                         @RequestParam String tipo,
                         @RequestParam String motivo,
                         Authentication authentication) {
        try {
            unpublish.unpublish(UnpublishCommand.of(id, tipo, motivo, AdminActor.from(authentication)));
            return new ModelAndView("redirect:/admin/articulos/" + id);
        } catch (DomainException e) {
            return render(id, statusFor(e), e.getMessage());
        } catch (IllegalArgumentException e) {
            return render(id, HttpStatus.BAD_REQUEST, "Tipo de retirada desconocido: " + tipo);
        }
    }

    private ModelAndView render(String id, HttpStatus status, String error) {
        var texto = articleText.byId(UUID.fromString(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

        String estado = texto.state();

        ModelAndView mav = status == null
                ? new ModelAndView("admin/articulo")
                : new ModelAndView("admin/articulo", status);

        mav.addObject("texto", texto);
        mav.addObject("estado", estado);
        mav.addObject("error", error);
        mav.addObject("auditoria", bandeja.auditOf(id));
        // RN2: las transiciones posibles las dicta el dominio, no la plantilla
        mav.addObject("transiciones", bandeja.transitionsFrom(estado));
        mav.addObject("etiquetas", ETIQUETAS);
        mav.addObject("versiones", historial.revisions(id));
        mav.addObject("publicado", "PUBLISHED".equals(estado));
        mav.addObject("tiposRetirada", bandeja.retirementKinds());
        return mav;
    }

    /** El dominio devuelve nombres técnicos; aquí se traducen para quien escribe. */
    private static final java.util.Map<String, String> ETIQUETAS = java.util.Map.of(
            "IN_REVIEW", "Enviar a revisión",
            "APPROVED", "Aprobar",
            "SCHEDULED", "Programar",
            "PUBLISHED", "Publicar",
            "ARCHIVED", "Archivar");

    private static HttpStatus statusFor(DomainException e) {
        String code = e.code() == null ? "" : e.code();
        if (code.contains("FORBIDDEN") || code.contains("CANNOT_PUBLISH") || code.contains("NOT_ALLOWED")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.contains("REASON") || code.contains("MOTIVO") || code.contains("BODY_REQUIRED")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        if (code.contains("CONCURRENT_EDIT")) {
            return HttpStatus.CONFLICT;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
