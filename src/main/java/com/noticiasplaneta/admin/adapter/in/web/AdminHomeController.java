package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.news.application.port.in.ListArticlesUseCase;
import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase;
import com.noticiasplaneta.news.application.port.in.PublishBatchUseCase.BatchPublishCommand;
import com.noticiasplaneta.shared.domain.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

/**
 * Bandeja del panel: listado y publicación por lotes (F5).
 *
 * <p>Bajo la cadena de {@code /admin/**}: sesión y CSRF obligatorios. Habla con el módulo
 * {@code news} solo por sus puertos de entrada; quién puede publicar lo decide el dominio,
 * no este controlador.
 */
@Controller
class AdminHomeController {

    private static final int POR_PAGINA = 40;

    /** Valor del checkbox «seleccionar todo de la página». */
    static final String TODOS_DE_LA_PAGINA = "pagina";

    private final ListArticlesUseCase articles;
    private final PublishBatchUseCase lote;

    AdminHomeController(ListArticlesUseCase articles, PublishBatchUseCase lote) {
        this.articles = articles;
        this.lote = lote;
    }

    @GetMapping("/admin")
    String bandeja(@RequestParam(name = "estado", required = false) String estado,
                   @RequestParam(name = "seccion", required = false) String seccion,
                   @RequestParam(name = "q", required = false) String q,
                   ModelMap model) {
        poblar(model, estado, seccion, q, null);
        return "admin/bandeja";
    }

    /**
     * Publica lo seleccionado. Dos caminos para no depender de JavaScript:
     *
     * <ul>
     *   <li>las casillas marcadas viajan como {@code ids};</li>
     *   <li>si el navegador no ejecuta el script y solo se marcó «toda la página», el
     *       servidor recalcula esa página con los mismos filtros.</li>
     * </ul>
     */
    @PostMapping("/admin/articulos/publicar-lote")
    ModelAndView publicarLote(@RequestParam(name = "ids", required = false) List<String> ids,
                              @RequestParam(name = "todos", required = false) String todos,
                              @RequestParam(name = "estado", required = false) String estado,
                              @RequestParam(name = "seccion", required = false) String seccion,
                              @RequestParam(name = "q", required = false) String q,
                              Authentication authentication,
                              RedirectAttributes redirect) {

        List<String> solicitados = new ArrayList<>();
        if (ids != null) {
            solicitados.addAll(ids);
        }
        if (TODOS_DE_LA_PAGINA.equals(todos)) {
            articles.list(estado, seccion, q, POR_PAGINA).forEach(a -> solicitados.add(a.id()));
        }

        if (solicitados.isEmpty()) {
            redirect.addFlashAttribute("errorLote", "No hay nada seleccionado: marca alguna casilla.");
            return volverABandeja(redirect, estado, seccion, q);
        }

        try {
            var resultado = lote.publish(new BatchPublishCommand(solicitados, AdminActor.from(authentication)));
            redirect.addFlashAttribute("resumenLote", resultado);
            return volverABandeja(redirect, estado, seccion, q);
        } catch (DomainException e) {
            // lote rechazado entero (sin permiso, demasiado grande): no se ha publicado nada
            var mav = new ModelAndView("admin/bandeja", statusFor(e));
            poblar(mav.getModelMap(), estado, seccion, q, e.getMessage());
            return mav;
        }
    }

    private static ModelAndView volverABandeja(RedirectAttributes redirect, String estado,
                                               String seccion, String q) {
        if (estado != null && !estado.isBlank()) {
            redirect.addAttribute("estado", estado);
        }
        if (seccion != null && !seccion.isBlank()) {
            redirect.addAttribute("seccion", seccion);
        }
        if (q != null && !q.isBlank()) {
            redirect.addAttribute("q", q);
        }
        return new ModelAndView("redirect:/admin");
    }

    private void poblar(ModelMap model, String estado, String seccion, String q, String error) {
        model.addAttribute("estado", estado);
        model.addAttribute("seccion", seccion);
        model.addAttribute("q", q);
        model.addAttribute("error", error);
        model.addAttribute("contadores", articles.countsByState());
        model.addAttribute("articulos", articles.list(estado, seccion, q, POR_PAGINA));
        model.addAttribute("topeLote", PublishBatchUseCase.MAX_IDS);
    }

    private static HttpStatus statusFor(DomainException e) {
        String code = e.code() == null ? "" : e.code();
        if (code.contains("FORBIDDEN") || code.contains("AGENT_CANNOT_PUBLISH")) {
            return HttpStatus.FORBIDDEN;
        }
        if (code.contains("TOO_LARGE")) {
            return HttpStatus.UNPROCESSABLE_ENTITY;
        }
        return HttpStatus.BAD_REQUEST;
    }
}
