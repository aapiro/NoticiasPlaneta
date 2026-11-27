package com.noticiasplaneta.web.adapter.in;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Datos comunes de SEO para todas las páginas: URL base y ruta actual.
 *
 * <p>Se resuelven una vez aquí y no en cada controlador, para que el canonical y
 * las etiquetas Open Graph no dependan de que alguien se acuerde de pasarlos.
 */
@ControllerAdvice
class SeoModelAdvice {

    private final String baseUrl;

    SeoModelAdvice(@Value("${noticias.base-url:https://localhost}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @ModelAttribute("baseUrl")
    String baseUrl() {
        return baseUrl;
    }

    /** Ruta actual con su query, para el canonical. */
    @ModelAttribute("rutaActual")
    String rutaActual(HttpServletRequest request) {
        String query = request.getQueryString();
        return request.getRequestURI() + (query == null || query.isBlank() ? "" : "?" + query);
    }

    /** Ruta sin query, para construir los enlaces de paginación. */
    @ModelAttribute("rutaSinPagina")
    String rutaSinPagina(HttpServletRequest request) {
        return request.getRequestURI();
    }
}
