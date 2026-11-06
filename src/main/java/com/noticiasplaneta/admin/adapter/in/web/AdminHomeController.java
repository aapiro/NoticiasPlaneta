package com.noticiasplaneta.admin.adapter.in.web;

import com.noticiasplaneta.news.application.port.in.ListArticlesUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class AdminHomeController {

    private final ListArticlesUseCase articles;

    AdminHomeController(ListArticlesUseCase articles) {
        this.articles = articles;
    }

    @GetMapping("/admin")
    String bandeja(@RequestParam(name = "estado", required = false) String estado,
                   @RequestParam(name = "seccion", required = false) String seccion,
                   @RequestParam(name = "q", required = false) String q,
                   Model model) {

        model.addAttribute("estado", estado);
        model.addAttribute("seccion", seccion);
        model.addAttribute("q", q);
        model.addAttribute("contadores", articles.countsByState());
        model.addAttribute("articulos", articles.list(estado, seccion, q, 40));
        return "admin/bandeja";
    }
}
