package com.noticiasplaneta.search.adapter.in.web;

import com.noticiasplaneta.search.application.port.in.SearchArticlesUseCase;
import com.noticiasplaneta.search.application.port.in.SearchHit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/public/search")
class SearchController {

    private final SearchArticlesUseCase search;

    SearchController(SearchArticlesUseCase search) {
        this.search = search;
    }

    @GetMapping
    List<SearchHit> search(@RequestParam("q") String query,
                           @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return search.search(query, Math.min(limit, 50));
    }
}
