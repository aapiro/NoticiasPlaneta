package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetPublishedArticleUseCase;
import com.noticiasplaneta.news.application.port.in.PublishedArticle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/**
 * Página de artículo. Un artículo retirado responde 410 Gone durante 90 días (RN4 de F07);
 * aquí se sirve la nota de retirada sin el cuerpo.
 */
@Controller
class ArticlePageController {

    private final GetPublishedArticleUseCase articles;

    ArticlePageController(GetPublishedArticleUseCase articles) {
        this.articles = articles;
    }

    @GetMapping("/{sectionId}/{slug}")
    String articulo(@PathVariable String sectionId, @PathVariable String slug, Model model) {
        Optional<PublishedArticle> published = articles.bySectionAndSlug(sectionId, slug);
        if (published.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado");
        }
        PublishedArticle article = published.get();
        ArticleText texto = articles.textById(article.id()).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

        model.addAttribute("articulo", article);
        model.addAttribute("texto", texto);
        return "articulo";
    }
}
