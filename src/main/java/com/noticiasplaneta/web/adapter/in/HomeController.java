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

    @GetMapping("/")
    String portada(@RequestParam(name = "seccion", required = false) String seccion, Model model) {
        var portada = articles.latest(seccion, 30);

        model.addAttribute("titulo", "Noticias Planeta");
        model.addAttribute("seccion", seccion);
        model.addAttribute("articulos", portada);
        // una sola consulta para las imágenes principales: nada de N+1
        model.addAttribute("medios", medios.leadsOf(
                portada.stream().map(a -> a.id()).toList()));
        return "portada";
    }
}
