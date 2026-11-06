package com.noticiasplaneta.mcp.adapter.in.tools;

import com.noticiasplaneta.news.application.port.in.ArticleText;
import com.noticiasplaneta.news.application.port.in.CreateDraftCommand;
import com.noticiasplaneta.news.application.port.in.CreateDraftFromItemUseCase;
import com.noticiasplaneta.news.application.port.in.CreateDraftResult;
import com.noticiasplaneta.news.application.port.in.GetArticleTextUseCase;
import com.noticiasplaneta.search.application.port.in.SearchArticlesUseCase;
import com.noticiasplaneta.search.application.port.in.SearchHit;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * F12 — Herramientas que los agentes pueden invocar.
 *
 * <p>RN2: no existe herramienta para publicar. Un agente puede crear borradores, buscarlos
 * y leerlos; publicar sigue requiriendo un humano en la cadena de seguridad.
 */
@Component
public class NoticiasPlanetaTools {

    private final SearchArticlesUseCase search;
    private final CreateDraftFromItemUseCase createDraft;
    private final GetArticleTextUseCase articleText;

    NoticiasPlanetaTools(SearchArticlesUseCase search, CreateDraftFromItemUseCase createDraft,
                         GetArticleTextUseCase articleText) {
        this.search = search;
        this.createDraft = createDraft;
        this.articleText = articleText;
    }

    public record Noticia(String articleId, String slug, String seccion, String titular) {
    }

    @McpTool(name = "buscar_noticias",
            description = "Busca artículos publicados por texto libre. Devuelve id, slug, sección y titular.")
    public List<Noticia> buscarNoticias(
            @McpToolParam(description = "Texto a buscar", required = true) String consulta,
            @McpToolParam(description = "Máximo de resultados (1-20)", required = false) Integer limite) {

        int max = limite == null ? 10 : Math.clamp(limite, 1, 20);
        return search.search(consulta, max).stream()
                .map(h -> new Noticia(h.articleId(), h.slug(), h.sectionId(), h.title()))
                .toList();
    }

    @McpTool(name = "leer_articulo",
            description = "Devuelve el texto completo de un artículo por su identificador.")
    public ArticleText leerArticulo(
            @McpToolParam(description = "Identificador del artículo", required = true) String id) {
        return articleText.byId(UUID.fromString(id))
                .orElseThrow(() -> new IllegalArgumentException("Artículo no encontrado: " + id));
    }

    @McpTool(name = "crear_borrador",
            description = "Crea un borrador en estado DRAFT. Un agente nunca puede publicar.")
    public CreateDraftResult crearBorrador(
            @McpToolParam(description = "Sección, por ejemplo 'economia'", required = true) String seccion,
            @McpToolParam(description = "Titular del borrador", required = true) String titular,
            @McpToolParam(description = "Cuerpo del artículo", required = false) String cuerpo,
            @McpToolParam(description = "Identificador del elemento de ingesta, si viene de un feed",
                    required = false) String ingestItemId) {

        return createDraft.create(CreateDraftCommand.byAgent(
                ingestItemId == null ? UUID.randomUUID().toString() : ingestItemId,
                seccion, titular, cuerpo == null ? "" : cuerpo, null));
    }
}
