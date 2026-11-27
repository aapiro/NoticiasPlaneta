package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class HomeController {

    private final ListPublishedArticlesUseCase articles;
    private final ListArticleMediaUseCase medios;

    HomeController(ListPublishedArticlesUseCase articles, ListArticleMediaUseCase medios) {
        this.articles = articles;
        this.medios = medios;
    }

    private static final int TAMANO_PAGINA = 24;

    @GetMapping("/")
    String portada(@RequestParam(name = "seccion", required = false) String seccion,
                   @RequestParam(name = "pagina", defaultValue = "1") int pagina,
                   Model model) {
        var page = articles.paginar(seccion, pagina, TAMANO_PAGINA);

        model.addAttribute("titulo", "Noticias Planeta");
        model.addAttribute("seccion", seccion);
        model.addAttribute("articulos", page.articulos());
        model.addAttribute("pagina", page);
        // una sola consulta para las imágenes principales: nada de N+1
        model.addAttribute("medios", medios.leadsOf(
                page.articulos().stream().map(a -> a.id()).toList()));
        return "portada";
    }
}
