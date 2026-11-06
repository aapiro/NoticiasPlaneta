package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.search.application.port.in.SearchArticlesUseCase;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
class SearchPageController {

    private final SearchArticlesUseCase search;

    SearchPageController(SearchArticlesUseCase search) {
        this.search = search;
    }

    @GetMapping("/buscar")
    String buscar(@RequestParam(name = "q", required = false) String q, Model model) {
        model.addAttribute("q", q);
        if (q == null || q.trim().length() < 2) {
            model.addAttribute("resultados", java.util.List.of());
            return "buscar";
        }
        model.addAttribute("resultados", search.search(q, 20));
        return "buscar";
    }
}
