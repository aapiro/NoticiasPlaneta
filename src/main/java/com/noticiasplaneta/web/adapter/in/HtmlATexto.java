package com.noticiasplaneta.web.adapter.in;

/**
 * HTML del feed convertido a texto plano.
 *
 * <p>El cuerpo llega con etiquetas y entidades. Un {@code content="&lt;p&gt;&lt;img…"}
 * es lo que ve WhatsApp al compartir: hay que limpiarlo antes de meterlo en metadatos
 * o en un RSS.
 */
final class HtmlATexto {

    private HtmlATexto() {
    }

    static String resumen(String cuerpo, int max) {
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
        return limpio.length() <= max ? limpio : limpio.substring(0, max - 1) + "…";
    }
}
