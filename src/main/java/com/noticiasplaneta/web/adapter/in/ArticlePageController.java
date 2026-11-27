package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.GetArticleSourcesUseCase;
import com.noticiasplaneta.news.application.port.in.GetPublishedArticleUseCase;
import com.noticiasplaneta.news.application.port.in.PublishedArticle;
import com.noticiasplaneta.news.application.port.in.RetiredArticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import java.time.format.DateTimeFormatter;
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
 *
 * <p>Además emite JSON-LD {@code NewsArticle} (backlog #1): es lo que permite que
 * Google entienda la pieza y que al compartirla salga titular y foto.
 */
@Controller
class ArticlePageController {

    private static final int RESUMEN_MAX = 160;
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private final GetPublishedArticleUseCase articles;
    private final ListArticleMediaUseCase medios;
    private final GetArticleSourcesUseCase fuentes;
    private final String baseUrl;

    ArticlePageController(GetPublishedArticleUseCase articles, ListArticleMediaUseCase medios,
                          GetArticleSourcesUseCase fuentes,
                          @Value("${noticias.base-url:https://localhost}") String baseUrl) {
        this.articles = articles;
        this.medios = medios;
        this.fuentes = fuentes;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping("/{sectionId}/{slug}")
    ModelAndView articulo(@PathVariable String sectionId, @PathVariable String slug) {
        Optional<PublishedArticle> published = articles.bySectionAndSlug(sectionId, slug);

        if (published.isPresent()) {
            PublishedArticle article = published.get();
            ArticleText texto = articles.textById(article.id()).orElseThrow(
                    () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Artículo no encontrado"));

            String url = baseUrl + "/" + sectionId + "/" + slug;
            String descripcion = resumen(texto.body());

            ModelAndView mav = new ModelAndView("articulo");
            mav.addObject("articulo", article);
            mav.addObject("texto", texto);
            mav.addObject("medios", medios.of(article.id()));
            mav.addObject("fuentes", fuentes.get(article.id()));
            mav.addObject("descripcion", descripcion);
            mav.addObject("jsonLd", jsonLd(article, url, descripcion));
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

    /** Resumen corto y en una línea, para description y Open Graph. Sin HTML. */
    private static String resumen(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) return "";
        String limpio = cuerpo
                .replaceAll("<[^>]*>", " ")      // etiquetas
                .replace("&nbsp;", " ")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")           // al final: no reintroducir entidades
                .replaceAll("\\s+", " ")
                .trim();
        return limpio.length() <= RESUMEN_MAX ? limpio : limpio.substring(0, RESUMEN_MAX - 1) + "…";
    }

    private String jsonLd(PublishedArticle article, String url, String descripcion) {
        StringBuilder json = new StringBuilder(512);
        json.append("{\"@context\":\"https://schema.org\",\"@type\":\"NewsArticle\"");
        json.append(",\"headline\":\"").append(escapar(article.title())).append('"');
        json.append(",\"mainEntityOfPage\":{\"@type\":\"WebPage\",\"@id\":\"").append(escapar(url)).append("\"}");
        json.append(",\"url\":\"").append(escapar(url)).append('"');
        if (article.publishedAt() != null) {
            String fecha = ISO.format(article.publishedAt());
            json.append(",\"datePublished\":\"").append(fecha).append('"');
            json.append(",\"dateModified\":\"").append(fecha).append('"');
        }
        if (!descripcion.isBlank()) {
            json.append(",\"description\":\"").append(escapar(descripcion)).append('"');
        }
        if (article.imageUrl() != null && !article.imageUrl().isBlank()) {
            json.append(",\"image\":[\"").append(escapar(article.imageUrl())).append("\"]");
        }
        json.append(",\"articleSection\":\"").append(escapar(article.sectionId())).append('"');
        json.append(",\"inLanguage\":\"es\"");
        json.append(",\"author\":{\"@type\":\"Organization\",\"name\":\"Noticias Planeta\"}");
        json.append(",\"publisher\":{\"@type\":\"Organization\",\"name\":\"Noticias Planeta\",")
            .append("\"url\":\"").append(escapar(baseUrl)).append("\"}");
        json.append('}');
        return json.toString();
    }

    /** JSON no admite saltos ni comillas sueltas: se escapa lo imprescindible. */
    private static String escapar(String texto) {
        if (texto == null) return "";
        StringBuilder out = new StringBuilder(texto.length() + 16);
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
