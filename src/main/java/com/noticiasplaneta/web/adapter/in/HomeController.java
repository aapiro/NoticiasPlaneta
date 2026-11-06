package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class HomeController {

    private final ListPublishedArticlesUseCase articles;

    HomeController(ListPublishedArticlesUseCase articles) {
        this.articles = articles;
    }

    @GetMapping("/")
    String portada(@RequestParam(name = "seccion", required = false) String seccion, Model model) {
        model.addAttribute("titulo", "Noticias Planeta");
        model.addAttribute("seccion", seccion);
        model.addAttribute("articulos", articles.latest(seccion, 30));
        return "portada";
    }
}
