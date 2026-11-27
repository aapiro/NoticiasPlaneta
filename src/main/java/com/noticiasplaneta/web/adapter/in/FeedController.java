package com.noticiasplaneta.web.adapter.in;

import com.noticiasplaneta.media.application.port.in.ListArticleMediaUseCase;
import com.noticiasplaneta.news.application.port.in.FeedItem;
import com.noticiasplaneta.news.application.port.in.ListPublishedArticlesUseCase;
import com.noticiasplaneta.section.application.port.in.SectionCatalogUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * RSS de salida (backlog #5).
 *
 * <p>Hasta ahora el portal solo consumía feeds. Publicar el suyo es lo que hace que
 * otros agregadores citen las piezas en vez de ignorarlas.
 *
 * <p>RSS 2.0 con {@code atom:link} (autodescubrimiento) y {@code media:content}
 * (la foto en el lector). El {@code guid} es la URL canónica y es permanente.
 */
@Controller
class FeedController {

    private static final int PIEZAS = 40;
    private static final int RESUMEN_MAX = 300;

    /** RFC 822, obligatorio en RSS 2.0. En inglés: el formato no se localiza. */
    private static final DateTimeFormatter RFC822 =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)
                    .withZone(ZoneId.of("Europe/Madrid"));

    private final ListPublishedArticlesUseCase articles;
    private final ListArticleMediaUseCase medios;
    private final SectionCatalogUseCase secciones;
    private final String baseUrl;

    FeedController(ListPublishedArticlesUseCase articles, ListArticleMediaUseCase medios,
                   SectionCatalogUseCase secciones,
                   @Value("${noticias.base-url:https://noticiasplaneta.com}") String baseUrl) {
        this.articles = articles;
        this.medios = medios;
        this.secciones = secciones;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @GetMapping(value = "/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    @ResponseBody
    String sitio() {
        return rss(null, "Noticias Planeta",
                "Lo que pasa, en cinco minutos y con el enlace a la fuente original.",
                baseUrl + "/feed.xml");
    }

    @GetMapping(value = "/seccion/{slug}/feed.xml", produces = "application/rss+xml;charset=UTF-8")
    @ResponseBody
    String seccion(@PathVariable String slug) {
        var seccion = secciones.porSlug(slug).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sección desconocida"));

        String descripcion = seccion.descripcion();
        if (descripcion == null || descripcion.isBlank()) {
            descripcion = seccion.nombre() + " — Noticias Planeta";
        }
        return rss(slug, seccion.nombre() + " — Noticias Planeta", descripcion,
                baseUrl + "/seccion/" + slug + "/feed.xml");
    }

    private String rss(String sectionId, String titulo, String descripcion, String self) {
        var items = articles.feed(sectionId, PIEZAS);
        var leads = medios.leadsOf(items.stream().map(FeedItem::id).toList());

        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<rss version=\"2.0\"")
                .append(" xmlns:atom=\"http://www.w3.org/2005/Atom\"")
                .append(" xmlns:media=\"http://search.yahoo.com/mrss/\">\n");
        xml.append("<channel>\n");
        xml.append("<title>").append(esc(titulo)).append("</title>\n");
        xml.append("<link>").append(esc(baseUrl + "/")).append("</link>\n");
        xml.append("<description>").append(esc(descripcion)).append("</description>\n");
        xml.append("<language>es</language>\n");
        xml.append("<copyright>Noticias Planeta</copyright>\n");
        xml.append("<atom:link href=\"").append(esc(self))
                .append("\" rel=\"self\" type=\"application/rss+xml\"/>\n");
        xml.append("<lastBuildDate>").append(RFC822.format(Instant.now())).append("</lastBuildDate>\n");

        for (FeedItem item : items) {
            String url = baseUrl + "/" + item.sectionId() + "/" + item.slug();
            xml.append("<item>\n");
            xml.append("<title>").append(esc(item.title())).append("</title>\n");
            xml.append("<link>").append(esc(url)).append("</link>\n");
            // Permanente y estable: la URL no cambia aunque el titular se retoque.
            xml.append("<guid isPermaLink=\"true\">").append(esc(url)).append("</guid>\n");
            if (item.publishedAt() != null) {
                xml.append("<pubDate>").append(RFC822.format(item.publishedAt())).append("</pubDate>\n");
            }
            xml.append("<description>")
                    .append(esc(HtmlATexto.resumen(item.body(), RESUMEN_MAX)))
                    .append("</description>\n");
            xml.append("<category>").append(esc(item.sectionId())).append("</category>\n");

            // La foto propia manda sobre la del medio de origen, igual que en el portal.
            var lead = leads.get(item.id());
            String foto = lead != null ? baseUrl + "/media/" + storagePath(lead.storageKey()) : item.imageUrl();
            if (foto != null && !foto.isBlank()) {
                xml.append("<enclosure url=\"").append(esc(foto))
                        .append("\" type=\"image/jpeg\" length=\"0\"/>\n");
                xml.append("<media:content url=\"").append(esc(foto))
                        .append("\" medium=\"image\"/>\n");
            }
            xml.append("</item>\n");
        }

        xml.append("</channel>\n</rss>\n");
        return xml.toString();
    }

    private static String storagePath(String storageKey) {
        int corte = storageKey.indexOf("media/");
        return corte >= 0 ? storageKey.substring(corte + "media/".length()) : storageKey;
    }

    /** XML no perdona un {@code &} ni un {@code <} sueltos. */
    static String esc(String texto) {
        if (texto == null) return "";
        return texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
