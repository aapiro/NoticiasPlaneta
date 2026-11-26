package com.noticiasplaneta.mcp.adapter.in.tools;

import com.noticiasplaneta.generation.application.port.in.GenerarBorradorCommand;
import com.noticiasplaneta.generation.application.port.in.GenerarBorradorUseCase;
import com.noticiasplaneta.generation.application.port.in.ResultadoGeneracion;
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
    private final GenerarBorradorUseCase generador;

    NoticiasPlanetaTools(SearchArticlesUseCase search, CreateDraftFromItemUseCase createDraft,
                         GetArticleTextUseCase articleText, GenerarBorradorUseCase generador) {
        this.search = search;
        this.createDraft = createDraft;
        this.articleText = articleText;
        this.generador = generador;
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

    /**
     * F8 — el agente pide una pieza montada. Se pasa {@code agente(...)}, que no permite
     * publicación automática: un agente nunca publica (RN5 de F05), aunque la sección
     * tenga PUBLISH_AUTO.
     */
    @McpTool(name = "generar_borrador_desde_tema",
            description = "Genera un borrador en DRAFT a partir de un tema o titular y una sección, "
                    + "usando solo material ya ingerido en los últimos 30 días. Devuelve las fuentes "
                    + "usadas o el motivo por el que no hay material suficiente. Nunca publica.")
    public ResultadoGeneracion generarBorradorDesdeTema(
            @McpToolParam(description = "Tema o titular, por ejemplo 'sequía en Cataluña'",
                    required = true) String tema,
            @McpToolParam(description = "Sección de destino, por ejemplo 'economia'",
                    required = true) String seccion) {
        return generador.generar(GenerarBorradorCommand.agente(tema, seccion, "agente-mcp"));
    }
}
