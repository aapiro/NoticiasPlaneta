package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import com.noticiasplaneta.section.application.port.in.SectionCatalogUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Página de sección (backlog #3).
 *
 * <p>Además del listado paginado, declara al lector el enfoque editorial de la
 * sección, que es obligatorio según ADR-012: un sesgo oculto no es una línea
 * editorial, es un engaño.
 */
@Controller
class SectionPageController {

    private static final int TAMANO_PAGINA = 24;

    private final ListPublishedArticlesUseCase articles;
    private final ListArticleMediaUseCase medios;
    private final SectionCatalogUseCase secciones;

    SectionPageController(ListPublishedArticlesUseCase articles, ListArticleMediaUseCase medios,
                          SectionCatalogUseCase secciones) {
        this.articles = articles;
        this.medios = medios;
        this.secciones = secciones;
    }

    @GetMapping("/seccion/{slug}")
    String seccion(@PathVariable String slug,
                   @RequestParam(name = "pagina", defaultValue = "1") int pagina,
                   Model model) {

        var seccion = secciones.porSlug(slug).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sección desconocida"));

        var page = articles.paginar(slug, pagina, TAMANO_PAGINA);

        model.addAttribute("titulo", seccion.nombre() + " — Noticias Planeta");
        model.addAttribute("seccionActual", seccion);
        model.addAttribute("descripcion", seccion.descripcion());
        model.addAttribute("articulos", page.articulos());
        model.addAttribute("pagina", page);
        model.addAttribute("medios", medios.leadsOf(
                page.articulos().stream().map(a -> a.id()).toList()));
        return "seccion";
    }
}
