package com.noticiasplaneta.generation.application.port.in;

import java.util.List;

/**
 * Informe de la generación. Es lo que ve el editor (y el agente por MCP): si hay pieza,
 * qué fuentes la sostienen; si no la hay, por qué no.
 *
 * <p>Los tipos son propios del puerto, sin dominio de otros módulos: cualquier módulo
 * puede consumirlo sin conocer los internals de {@code generation}.
 */
public record ResultadoGeneracion(
        Estado estado,
        String motivo,
        String explicacion,
        String articleId,
        String slug,
        String seccion,
        String titular,
        String cuerpo,
        String enfoque,
        String promptVersion,
        String modelo,
        String proveedor,
        long costeMicros,
        int fuentesIndependientes,
        int fragmentosRecogidos,
        boolean publicado,
        List<Fuente> fuentes,
        List<Parrafo> parrafos,
        List<Descarte> descartes) {

    public enum Estado {
        /** Borrador creado, pendiente de revisión humana. */
        BORRADOR,
        /** Borrador creado y publicado por la puerta PUBLISH_AUTO. */
        PUBLICADO,
        /** No hay pieza y se explica por qué. */
        ABORTADO
    }

    /** Fragmento usado, con medio, URL y fecha: la trazabilidad que pide el encargo. */
    public record Fuente(int ref, String medio, String url, String fecha) {
    }

    /** Párrafo entregado y las fuentes que lo sostienen. */
    public record Parrafo(String texto, List<Integer> citas) {
    }

    public record Descarte(String motivo, String detalle) {
    }

    public ResultadoGeneracion {
        fuentes = fuentes == null ? List.of() : List.copyOf(fuentes);
        parrafos = parrafos == null ? List.of() : List.copyOf(parrafos);
        descartes = descartes == null ? List.of() : List.copyOf(descartes);
    }

    public boolean ok() {
        return estado != Estado.ABORTADO;
    }

    /** Sin pieza: no se ha creado artículo. La explicación va en español, para el editor. */
    public static ResultadoGeneracion abortado(String tema, String seccion, String motivo,
                                               String explicacion, int fuentes, int dominios) {
        return new ResultadoGeneracion(Estado.ABORTADO, motivo, explicacion, null, null, seccion,
                null, null, null, null, null, null, 0, dominios, fuentes, false,
                List.of(), List.of(), List.of());
    }
}
