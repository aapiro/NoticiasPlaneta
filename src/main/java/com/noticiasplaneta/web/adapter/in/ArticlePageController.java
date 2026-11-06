package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetPublishedArticleUseCase;
import com.noticiasplaneta.news.application.port.in.PublishedArticle;
import com.noticiasplaneta.news.application.port.in.RetiredArticle;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.util.Optional;

/**
 * Página de artículo.
 *
 * <p>Distingue tres casos, y la diferencia importa (RN4 de F07):
 * <ul>
 *   <li>publicado → 200 con el texto</li>
 *   <li>retirado → <b>410 Gone</b> con la nota de retirada, sin el cuerpo</li>
 *   <li>nunca existió → 404</li>
 * </ul>
 */
@Controller
class ArticlePageController {

    private final GetPublishedArticleUseCase articles;
    private final ListArticleMediaUseCase medios;

    ArticlePageController(GetPublishedArticleUseCase articles, ListArticleMediaUseCase medios) {
        this.articles = articles;
        this.medios = medios;
    }

    @GetMapping("/{sectionId}/{slug}")
    ModelAndView articulo(@PathVariable String sectionId, @PathVariable String slug) {
        Optional<PublishedArticle> published = articles.bySectionAndSlug(sectionId, slug);

        if (published.isPresent()) {
            PublishedArticle article = published.get();
            ArticleText texto = articles.textById(article.id()).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

            ModelAndView mav = new ModelAndView("articulo");
            mav.addObject("articulo", article);
            mav.addObject("texto", texto);
            mav.addObject("medios", medios.of(article.id()));
            return mav;
        }

        Optional<RetiredArticle> retired = articles.retiredBySectionAndSlug(sectionId, slug);
        if (retired.isPresent()) {
            ModelAndView mav = new ModelAndView("retirado", HttpStatus.GONE);
            mav.addObject("retirado", retired.get());
            return mav;
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado");
    }
}
