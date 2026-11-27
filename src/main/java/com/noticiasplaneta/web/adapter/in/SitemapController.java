package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import com.noticiasplaneta.section.application.port.in.SectionAdminUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * SEO técnico: sitemap y robots (backlog #1).
 *
 * <p>Sin esto Google no descubre las noticias y los enlaces compartidos salen sin
 * vista previa. El sitemap solo lista lo publicado: un borrador no existe para el mundo.
 */
@RestController
class SitemapController {

    private static final int TOPE = 5000;
    private static final DateTimeFormatter FECHA =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private final ListPublishedArticlesUseCase articles;
    private final SectionAdminUseCase secciones;
    private final String baseUrl;

    SitemapController(ListPublishedArticlesUseCase articles,
                      SectionAdminUseCase secciones,
                      @Value("${noticias.base-url:https://localhost}") String baseUrl) {
        this.articles = articles;
        this.secciones = secciones;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    String robots() {
        return """
                User-agent: *
                Allow: /
                Disallow: /admin
                Disallow: /admin/

                Sitemap: %s/sitemap.xml
                """.formatted(baseUrl);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    String sitemap() {
        StringBuilder xml = new StringBuilder(1024);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
           .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");

        // Portada y secciones: lo que cambia a menudo.
        url(xml, baseUrl + "/", null, "hourly", "1.0");
        for (var seccion : secciones.sections()) {
            if (seccion.activa()) {
                url(xml, baseUrl + "/seccion/" + seccion.slug(), null, "hourly", "0.8");
            }
        }

        // Noticias publicadas, con su fecha para que Google sepa qué es nuevo.
        for (var articulo : articles.latest(null, TOPE)) {
            String lastmod = articulo.publishedAt() == null ? null : FECHA.format(articulo.publishedAt());
            url(xml, baseUrl + "/" + articulo.sectionId() + "/" + articulo.slug(), lastmod, "daily", "0.6");
        }

        xml.append("</urlset>\n");
        return xml.toString();
    }

    private static void url(StringBuilder xml, String loc, String lastmod, String freq, String prioridad) {
        xml.append("  <url><loc>").append(escapar(loc)).append("</loc>");
        if (lastmod != null) {
            xml.append("<lastmod>").append(lastmod).append("</lastmod>");
        }
        xml.append("<changefreq>").append(freq).append("</changefreq>")
           .append("<priority>").append(prioridad).append("</priority></url>\n");
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
